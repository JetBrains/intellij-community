// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * Records what the JDK path matcher answers for every case of the Go glob test.
 * Run {@code java RecordPathMatcher.java > java-path-matcher.txt} in this directory. The record header names the JDK.
 * Each output line holds a quoted pattern, a quoted name and {@code true} or {@code false}.
 * A pattern the JDK refuses gets one line with an empty name and {@code error}.
 * The first entry of a case is the pattern. The other entries are the names.
 */
public final class RecordPathMatcher {
  private static final String[][] CASES = {
    // The module excludes the plugin layouts declare.
    {"ngCli/**", "ngCli/index.js", "ngCli/a/b.js", "ngCli", "ngCli/", "xngCli/a", "a/ngCli/b", "ngcli/a"},
    {"angular-service/**", "angular-service/main.js", "angular-service"},
    {"server/**", "server/tailwind.js", "server", "servers/x"},
    {"language-server/**", "language-server/prisma.js", "language-server/"},
    {"vue-service/**", "vue-service/index.js", "a/vue-service/index.js"},
    {"js/**", "js/a.js", "js/a/b.js", "js", "jsx/a.js", "a/js/b.js"},
    {"javascript/**", "javascript/x.js", "javascript"},
    {"js_reporter/**", "js_reporter/karma.js", "js_reporter"},
    {"standardDsls/**", "standardDsls/a.gdsl", "standardDsls/a/b.gdsl", "standardDsls", "standarddsls/a.gdsl"},
    {"com/jetbrains/builtInHelp/indexer/**", "com/jetbrains/builtInHelp/indexer/Indexer.class", "com/jetbrains/builtInHelp/Help.class"},
    {"mockito-extensions/**", "mockito-extensions/org.mockito.plugins.MockMaker", "META-INF/mockito-extensions/x"},
    {"rubystubs*/**", "rubystubs31/a.rb", "rubystubs/a.rb", "rubystubs3/1/a.rb", "rubystubs31", "a/rubystubs31/a.rb", "rubystubsX"},
    {"rubysigs*/**", "rubysigs33/a.rbs", "rubysigs"},
    // commonModuleExcludes.
    {"**/icon-robots.txt", "a/icon-robots.txt", "a/b/icon-robots.txt", "icon-robots.txt", "a/icon-robots.txt.bak", "a/xicon-robots.txt", "a/icon-robotsXtxt"},
    {"icon-robots.txt", "icon-robots.txt", "a/icon-robots.txt", "icon-robotsXtxt"},
    {".unmodified", ".unmodified", "a/.unmodified", "Xunmodified"},
    {".hash", ".hash", "a/.hash"},
    {"classpath.index", "classpath.index", "classpathXindex", "a/classpath.index"},
    {"module-info.class", "module-info.class", "META-INF/versions/9/module-info.class"},
    // Grammar: wildcards.
    {"*.txt", "a.txt", ".txt", "a/b.txt", "atxt"},
    {"**.txt", "a/b.txt", "a.txt", ".txt"},
    {"a?c", "abc", "a/c", "ac", "abbc"},
    {"**/*.class", "a/B.class", "a/b/C.class", "D.class"},
    {"**", "a", "a/b/c", "", "ab", "a b", "a\nb"},
    {"*", "a", "", "a/b", "ab"},
    {"?", "a", "", "ab", "/"},
    {"a*b", "ab", "ab", "a/b"},
    // Grammar: classes.
    {"[abc].txt", "a.txt", "c.txt", "d.txt", "ab.txt"},
    {"[!abc].txt", "d.txt", "a.txt", "/.txt"},
    {"[a-c].txt", "b.txt", "d.txt", "B.txt"},
    {"[!a-c].txt", "d.txt", "b.txt"},
    {"x[+-0]y", "x.y", "x0y", "x+y", "x/y", "x1y"},
    {"x[!a]y", "xby", "xay", "x/y"},
    {"[^a].txt", "^.txt", "a.txt", "b.txt"},
    {"[-a].txt", "-.txt", "a.txt", "b.txt"},
    {"[a-].txt", "a.txt", "-.txt", "b.txt"},
    {"[!-a].txt", "b.txt", "-.txt", "a.txt"},
    {"[a\\]b].txt", "ab].txt", "\\b].txt", "bb].txt"},
    {"[a&&b].txt", "a.txt", "&.txt", "b.txt", "c.txt"},
    {"[[a].txt", "[.txt", "a.txt", "b.txt"},
    // Grammar: groups.
    {"{a,b}.txt", "a.txt", "b.txt", "c.txt", "{a,b}.txt"},
    {"{a,b*}/x", "a/x", "bcd/x", "b/c/x"},
    {"{**/a,b}", "c/d/a", "b", "a"},
    {"{,a}b", "b", "ab", "cb"},
    // Grammar: escapes and literals.
    {"\\*.txt", "*.txt", "a.txt"},
    {"\\{a\\}", "{a}", "a"},
    {"\\[a\\]", "[a]", "a"},
    {"a,b", "a,b", "a"},
    {"a}b", "a}b", "ab"},
    {"a\\\\b", "a\\b", "ab"},
    {"a.b", "a.b", "aXb"},
    {"a+b(c)|d$e^f", "a+b(c)|d$e^f", "aab(c)|d$e^f"},
    {"README", "README", "readme"},
    {"ü/**", "ü/x", "u/x"},
    // Path.of semantics: one trailing separator is not part of the name.
    {"dir", "dir", "dir/", "dir/x"},
    {"dir/**", "dir/", "dir/x"},
    // Patterns the JDK refuses.
    {"{a"},
    {"{a,{b}}"},
    {"[a"},
    {"[a-"},
    {"[/]"},
    {"[b-a]"},
    {"[a-c-e]"},
    {"[--a]"},
    {"[^-a]"},
    {"a\\"},
    {"[]"},
    {"[!]"},
    {"[]a]"},
  };

  public static void main(String[] args) {
    FileSystem fileSystem = FileSystems.getDefault();
    System.out.println("# Recorded by RecordPathMatcher.java with " + System.getProperty("java.vendor") + " "
                       + System.getProperty("java.runtime.version") + " on " + System.getProperty("os.name") + ".");
    System.out.println("# pattern, name, result");
    for (String[] entry : CASES) {
      String pattern = entry[0];
      PathMatcher matcher;
      try {
        matcher = fileSystem.getPathMatcher("glob:" + pattern);
      }
      catch (IllegalArgumentException e) {
        System.out.println(quote(pattern) + "\t\"\"\terror");
        continue;
      }
      if (entry.length == 1) {
        throw new IllegalStateException("The JDK accepted " + pattern + ", which the case list expects it to refuse");
      }
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
