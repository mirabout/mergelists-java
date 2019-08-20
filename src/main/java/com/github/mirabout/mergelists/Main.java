package com.github.mirabout.mergelists;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Entry {
    int num;
    String title;
    Long created;
    Long deleted;

    long getTimestamp() {
        if (created != null) {
            return created;
        }
        if (deleted != null) {
            return deleted;
        }
        throw new AssertionError("Contract violation: missing both `created` and `deleted` timestamp");
    }
}

class MergeBuilder {
    private final Map<Integer, Entry> buckets = new HashMap<>();

    /**
     * Produces an ascending by timestamp order of {@code Entry} instances.
     */
    private static final Comparator<Entry> ENTRY_COMPARATOR = (e1, e2) -> {
        return Long.compare(e1.getTimestamp(), e2.getTimestamp());
    };

    public void addEntries(Collection<Entry> entries) {
        for (Entry entry: entries) {
            // Box the key once. TODO: An optimized collection like Trove could be used.
            final Integer num = entry.num;
            Entry existing = buckets.get(num);
            if (existing != null && existing.getTimestamp() >= entry.getTimestamp()) {
                continue;
            }
            buckets.put(num, entry);
        }
    }

    public List<Entry> build() {
        List<Entry> result = new ArrayList<>(buckets.values());
        result.sort(ENTRY_COMPARATOR);
        return result;
    }
}

public class Main {
    private static final Gson GSON = new Gson();

    private static String readFileContent(String filename) throws Exception {
        final StringBuilder content = new StringBuilder();
        Files.lines(Paths.get(filename)).forEach(line -> {
            content.append(line).append('\n');
        });
        return content.toString();
    }

    private static List<Entry> readRawFileEntries(String filename) throws Exception {
        final File file = new File(filename);
        // Do some basic preliminary checks for more clear error messages
        if (!file.exists()) {
            throw new IOException("The file `" + file + "` does not exist");
        }
        if (!file.canRead()) {
            throw new IOException("The file `" + file + "` is not readable");
        }

        // Read the file contents first to separate IO errors from parsing errors.
        // This is not very efficient but is better for maintainability/usability.
        final String contents = readFileContent(filename);

        // Intercept JSON parsing errors (if any) for a more clear error message
        try {
            return Arrays.asList(GSON.fromJson(contents, Entry[].class));
        } catch (Exception e) {
            throw new Exception("Failed to parse the file " + file + " contents as JSON", e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar mergelists.jar <filename1> <filename2> ...");
            return;
        }

        final MergeBuilder builder = new MergeBuilder();
        try {
            for (String arg: args) {
                builder.addEntries(readRawFileEntries(arg));
            }
        } catch (Exception e) {
            System.err.println("An error has occurred while reading files: " + e.getMessage());
            return;
        }

        // An exception should not occur while serializing a valid list of objects
        System.out.println(GSON.toJson(builder.build()));
    }
}
