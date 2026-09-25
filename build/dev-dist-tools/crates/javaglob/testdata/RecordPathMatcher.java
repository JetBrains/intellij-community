// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/**
 * Records what the JDK path matcher answers for every case of the javaglob test.
 * Every pattern stays inside the subset that javaglob supports: literals, {@code *}, {@code **} and {@code {a,b}}.
 * Run {@code java RecordPathMatcher.java > java-path-matcher.txt} in this directory. The record header names the JDK.
 * Each output line holds a quoted pattern, a quoted name and {@code true} or {@code false}.
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
    // The other distinct patterns of the plan file corpus. The javaglob test requires a case for each corpus pattern.
    {"**/{setup.py,conftest.py}", "a/setup.py", "a/b/conftest.py", "setup.py", "a/setup.pyc"},
    {"{setup.py,conftest.py}", "setup.py", "conftest.py", "a/setup.py"},
    {"**/pydev/pydev_test*", "a/pydev/pydev_test.py", "a/pydev/pydev_tests/x.py", "pydev/pydev_test.py"},
    {"**/{tests,.idea}", "a/tests", "a/b/.idea", "tests", "a/tests/x"},
    {"bin/LLDBFrontend.exe", "bin/LLDBFrontend.exe", "bin/LLDBFrontendXexe"},
    {"bin/*", "bin/lldb", "bin/a/b", "bin"},
    {"LLDB.framework/Resources/darwin-debug", "LLDB.framework/Resources/darwin-debug", "LLDBXframework/Resources/darwin-debug"},
    {"*.properties", "a.properties", "a/b.properties"},
    {"*/fileTemplates/**", "a/fileTemplates/x.ft", "a/b/fileTemplates/x.ft", "fileTemplates/x.ft"},
    {"META-INF/extensions/**", "META-INF/extensions/a.xml", "META-INF/extensions"},
    {"linux/x64/**", "linux/x64/lib.so", "linux/x64", "linux/aarch64/lib.so"},
    {"*/inspectionDescriptions/**", "a/inspectionDescriptions/x.html", "inspectionDescriptions/x.html", "a/b/inspectionDescriptions/x.html"},
    {"*/intentionDescriptions/**", "a/intentionDescriptions/x.html", "intentionDescriptions/x.html", "a/b/intentionDescriptions/x.html"},
    {"*/postfixTemplates/**", "a/postfixTemplates/x.html", "postfixTemplates/x.html", "a/b/postfixTemplates/x.html"},
    {"LLDB.framework/Resources/debugserver", "LLDB.framework/Resources/debugserver", "a/LLDB.framework/Resources/debugserver", "LLDBXframework/Resources/debugserver"},
    {"LLDB.framework/Resources/lldb", "LLDB.framework/Resources/lldb", "a/LLDB.framework/Resources/lldb", "LLDBXframework/Resources/lldb"},
    {"LLDB.framework/Resources/lldb-argdumper", "LLDB.framework/Resources/lldb-argdumper", "a/LLDB.framework/Resources/lldb-argdumper", "LLDBXframework/Resources/lldb-argdumper"},
    {"LLDBFrontend", "LLDBFrontend", "a/LLDBFrontend"},
    {"Python.framework/Resources/Python.app/Contents/MacOS/Python", "Python.framework/Resources/Python.app/Contents/MacOS/Python", "a/Python.framework/Resources/Python.app/Contents/MacOS/Python", "PythonXframework/Resources/Python.app/Contents/MacOS/Python"},
    {"bin/**", "bin/a/b", "bin", "a/bin/b"},
    {"bin/LLDBFrontend", "bin/LLDBFrontend", "a/bin/LLDBFrontend"},
    {"bin/msvcp140.dll", "bin/msvcp140.dll", "a/bin/msvcp140.dll", "bin/msvcp140Xdll"},
    {"bin/vcruntime140.dll", "bin/vcruntime140.dll", "a/bin/vcruntime140.dll", "bin/vcruntime140Xdll"},
    {"bin/vcruntime140_1.dll", "bin/vcruntime140_1.dll", "a/bin/vcruntime140_1.dll", "bin/vcruntime140_1Xdll"},
    {"clangTidyDoc/**", "clangTidyDoc/a/b", "clangTidyDoc", "a/clangTidyDoc/b"},
    {"dlv/**", "dlv/a/b", "dlv", "a/dlv/b"},
    {"grape/**", "grape/a/b", "grape", "a/grape/b"},
    {"jcef/**", "jcef/a/b", "jcef", "a/jcef/b"},
    {"linux/aarch64/**", "linux/aarch64/a/b", "linux/aarch64", "a/linux/aarch64/b"},
    {"linux/aarch64/intellij-rust-native-helper", "linux/aarch64/intellij-rust-native-helper", "a/linux/aarch64/intellij-rust-native-helper"},
    {"linux/x64/intellij-rust-native-helper", "linux/x64/intellij-rust-native-helper", "a/linux/x64/intellij-rust-native-helper"},
    {"mac/aarch64/**", "mac/aarch64/a/b", "mac/aarch64", "a/mac/aarch64/b"},
    {"mac/aarch64/intellij-rust-native-helper", "mac/aarch64/intellij-rust-native-helper", "a/mac/aarch64/intellij-rust-native-helper"},
    {"mac/x64/**", "mac/x64/a/b", "mac/x64", "a/mac/x64/b"},
    {"mac/x64/intellij-rust-native-helper", "mac/x64/intellij-rust-native-helper", "a/mac/x64/intellij-rust-native-helper"},
    {"mingw-dependencies.json", "mingw-dependencies.json", "a/mingw-dependencies.json", "mingw-dependenciesXjson"},
    {"ninja", "ninja", "a/ninja"},
    {"pydev/pydev_test*", "pydev/pydev_test.py", "pydev/pydev_test/x.py", "a/pydev/pydev_test.py"},
    {"quickdoc/**", "quickdoc/a/b", "quickdoc", "a/quickdoc/b"},
    {"runtime/**", "runtime/a/b", "runtime", "a/runtime/b"},
    {"win/aarch64/**", "win/aarch64/a/b", "win/aarch64", "a/win/aarch64/b"},
    {"win/aarch64/intellij-rust-native-helper", "win/aarch64/intellij-rust-native-helper", "a/win/aarch64/intellij-rust-native-helper"},
    {"win/x64/**", "win/x64/a/b", "win/x64", "a/win/x64/b"},
    {"win/x64/intellij-rust-native-helper", "win/x64/intellij-rust-native-helper", "a/win/x64/intellij-rust-native-helper"},
    {"{platform:pattern}", "platform:pattern", "platform"},
    {"{tests,.idea}", "tests", ".idea", "a/tests"},
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
