// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * Records what the JDK path matcher answers for every case of the javaglob test: the constructs of the subset.
 * Every pattern stays inside the subset that javaglob supports: literals, {@code *}, {@code **} and {@code {a,b}}.
 * The patterns of the plan files have their own record in {@code build/dist-tools/plan-corpus/testdata} of the ultimate root.
 * Run {@code java RecordPathMatcher.java > java-path-matcher.txt} in this directory. The record header names the JDK.
 * Each output line holds a quoted pattern, a quoted name and {@code true} or {@code false}.
 * The first entry of a case is the pattern. The other entries are the names.
 */
public final class RecordPathMatcher {
  private static final String[][] CASES = {
    // Wildcards.
    {"*.txt", "a.txt", ".txt", "a/b.txt", "atxt"},
    {"**.txt", "a/b.txt", "a.txt", ".txt"},
    {"**/*.class", "a/B.class", "a/b/C.class", "D.class"},
    {"**", "a", "a/b/c", "", "ab", "a b", "a\nb"},
    {"*", "a", "", "a/b", "ab"},
    {"a*b", "ab", "ab", "a/b"},
    // Groups.
    {"{a,b}.txt", "a.txt", "b.txt", "c.txt", "{a,b}.txt"},
    {"{a,b*}/x", "a/x", "bcd/x", "b/c/x"},
    {"{**/a,b}", "c/d/a", "b", "a"},
    {"{,a}b", "b", "ab", "cb"},
    // Literals.
    {"a,b", "a,b", "a"},
    {"a.b", "a.b", "aXb"},
    {"a+b(c)|d$e^f", "a+b(c)|d$e^f", "aab(c)|d$e^f"},
    {"README", "README", "readme"},
    {"ü/**", "ü/x", "u/x"},
    // One trailing separator of a clean name is not part of the name.
    {"dir", "dir", "dir/", "dir/x"},
    {"dir/**", "dir/", "dir/x"},
  };

  public static void main(String[] args) {
    FileSystem fileSystem = FileSystems.getDefault();
    System.out.println("# Recorded by RecordPathMatcher.java with " + System.getProperty("java.vendor") + " "
                       + System.getProperty("java.runtime.version") + " on " + System.getProperty("os.name") + ".");
    System.out.println("# pattern, name, result");
    for (String[] entry : CASES) {
      String pattern = entry[0];
      PathMatcher matcher = fileSystem.getPathMatcher("glob:" + pattern);
      for (int i = 1; i < entry.length; i++) {
        System.out.println(quote(pattern) + "\t" + quote(entry[i]) + "\t" + matcher.matches(Path.of(entry[i])));
      }
    }
  }

  /** Writes a Go-quoted string. A backslash and a quote get a backslash. Every other control or non-ASCII character becomes a {@code \\uXXXX} escape. */
  private static String quote(String value) {
    StringBuilder result = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == '"') {
        result.append('\\').append(c);
      }
      else if (c < 0x20 || c > 0x7e) {
        result.append(String.format("\\u%04x", (int)c));
      }
      else {
        result.append(c);
      }
    }
    return result.append('"').toString();
  }
}
