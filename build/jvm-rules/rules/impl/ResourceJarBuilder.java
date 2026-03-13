package com.intellij.tools.build.bazel.rules;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a jar from explicit (jarPath, sourcePath) mappings.
 *
 * <p>The built-in Bazel zipper parser splits mappings on '=', which breaks when resource names
 * themselves contain '='. This tool accepts mappings as separate arguments and supports such paths.
 */
public final class ResourceJarBuilder {
  private ResourceJarBuilder() {}

  private record ResourceEntry(String jarPath, Path sourcePath) {}

  public static void main(String[] args) throws IOException {
    Path output = null;
    List<ResourceEntry> entries = new ArrayList<>();
    List<String> expandedArgs = expandParamsFiles(args);

    for (int i = 0; i < expandedArgs.size(); ) {
      String arg = expandedArgs.get(i++);
      switch (arg) {
        case "--output":
          if (i >= expandedArgs.size()) {
            throw new IllegalArgumentException("Missing value for --output");
          }
          output = Paths.get(expandedArgs.get(i++));
          break;
        case "--entry":
          if (i + 1 >= expandedArgs.size()) {
            throw new IllegalArgumentException("Missing values for --entry");
          }
          entries.add(new ResourceEntry(normalizeJarPath(expandedArgs.get(i++)), Paths.get(expandedArgs.get(i++))));
          break;
        default:
          throw new IllegalArgumentException("Unknown argument: " + arg);
      }
    }

    if (output == null) {
      throw new IllegalArgumentException("--output is required");
    }

    Path parent = output.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    entries.sort(Comparator.comparing(ResourceEntry::jarPath).thenComparing(e -> e.sourcePath.toString()));

    Set<String> seenEntries = new HashSet<>();
    try (OutputStream out = Files.newOutputStream(output);
        ZipOutputStream zip = new ZipOutputStream(out)) {
      for (ResourceEntry entry : entries) {
        if (!Files.exists(entry.sourcePath)) {
          throw new IllegalArgumentException("Resource does not exist: " + entry.sourcePath);
        }
        if (!seenEntries.add(entry.jarPath)) {
          throw new IllegalArgumentException("Duplicate resource path in jar: " + entry.jarPath);
        }

        ZipEntry zipEntry = new ZipEntry(entry.jarPath);
        zipEntry.setTime(0L);
        zip.putNextEntry(zipEntry);
        Files.copy(entry.sourcePath, zip);
        zip.closeEntry();
      }
    }
  }

  private static String normalizeJarPath(String rawPath) {
    String normalized = rawPath.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  private static List<String> expandParamsFiles(String[] args) throws IOException {
    List<String> result = new ArrayList<>(args.length);
    for (String arg : args) {
      if (!arg.startsWith("@")) {
        result.add(arg);
        continue;
      }

      Path paramsFile = Paths.get(arg.substring(1));
      for (String line : Files.readAllLines(paramsFile)) {
        if (line.endsWith("\r")) {
          line = line.substring(0, line.length() - 1);
        }
        if (!line.isEmpty()) {
          result.add(line);
        }
      }
    }
    return result;
  }
}
