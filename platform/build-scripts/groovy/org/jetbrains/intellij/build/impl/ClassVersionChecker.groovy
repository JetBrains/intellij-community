// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.util.lang.JavaVersion
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * <p>
 *   Recursively checks .class files in directories and .jar/.zip files to ensure that their versions
 *   do not exceed limits specified in the config map.
 * </p>
 * <p>
 *   The config map contains pairs of path prefixes (relative to the check root) to version limits.
 *   The limits are Java version strings (<code>"1.3"</code>, <code>"8"</code> etc.);
 *   empty strings are ignored (making the check always pass).
 *   The map must contain an empty path prefix (<code>""</code>) denoting the default version limit.
 * </p>
 * <p>Example: <code>["": "1.8", "lib/idea_rt.jar": "1.3"]</code>.</p>
 */
@CompileStatic
class ClassVersionChecker {
  private static class Rule {
    final String path
    final int version

    Rule(String path, int version) {
      this.path = path
      this.version = version
    }
  }

  private final List<Rule> myRules
  private int myJars, myClasses
  private List<String> myErrors

  ClassVersionChecker(Map<String, String> config) {
    myRules = config.entrySet().collect { new Rule(it.key, classVersion(it.value)) }.sort { -it.path.length() }.toList()
    if (myRules.isEmpty() || !myRules.last().path.isEmpty()) {
      throw new IllegalArgumentException("Invalid configuration: missing default version")
    }
  }

  static int classVersion(String version) {
    version.isEmpty() ? -1 : JavaVersion.parse(version).feature + 44  // 1.1 = 45
  }

  void checkVersions(BuildContext buildContext, File root) {
    buildContext.messages.block("Verifying class file versions") {
      myJars = 0
      myClasses = 0
      myErrors = []

      buildContext.messages.info("Checking with ${myRules.size()} rules in ${root} ...")
      if (root.isDirectory()) {
        visitDirectory(root, "")
      }
      else {
        visitFile(root.path, "", null)
      }

      if (myClasses == 0) {
        buildContext.messages.error("No classes found under ${root} - please check the configuration")
      }
      buildContext.messages.info("Done, checked ${myClasses} classes in ${myJars} JARs")
      if (!myErrors.isEmpty()) {
        myErrors.each { buildContext.messages.warning(it) }
        buildContext.messages.error("Failed with ${myErrors.size()} problems")
      }
    }
  }

  private void visitDirectory(File directory, String relPath) {
    for (File child in directory.listFiles()) {
      if (child.isDirectory()) {
        visitDirectory(child, join(relPath, '/', child.name))
      }
      else {
        visitFile(child.path, join(relPath, '/', child.name), null)
      }
    }
  }

  private void visitFile(String fullPath, String relPath, @Nullable ZipInputStream input) {
    if (fullPath.endsWith(".zip") || fullPath.endsWith(".jar")) {
      if (input != null) {
        visitZip(fullPath, relPath, new ZipInputStream(input))
      }
      else {
        new ZipInputStream(new FileInputStream(fullPath)).withStream { visitZip(fullPath, relPath, it) }
      }
    }
    else if (fullPath.endsWith(".class") && !(fullPath.endsWith("module-info.class") || fullPath.contains("/META-INF/versions/"))) {
      if (input != null) {
        checkVersion(relPath, input)
      }
      else {
        new FileInputStream(fullPath).withStream { checkVersion(relPath, it) }
      }
    }
  }

  private void visitZip(String fullPath, String relPath, ZipInputStream input) {
    myJars++
    ZipEntry entry
    while ((entry = input.nextEntry) != null) {
      if (!entry.isDirectory()) {
        visitFile(fullPath + "!/" + entry.name, join(relPath, "!/", entry.name), input)
      }
    }
  }

  private static String join(String prefix, String separator, String suffix) {
    prefix.isEmpty() ? suffix : prefix + separator + suffix
  }

  private void checkVersion(String path, InputStream stream) {
    myClasses++

    DataInputStream dataStream = new DataInputStream(stream)
    if (dataStream.readInt() != (int)0xCAFEBABE || dataStream.skipBytes(3) != 3) {
      myErrors.add(path + ": invalid .class file header")
      return
    }

    int major = dataStream.readUnsignedByte()
    if (major == 196653) major = 45
    if (major < 44 || major >= 100) {
      myErrors.add(path + ": suspicious .class file version: " + major)
      return
    }

    int expected = myRules.find { it.path.isEmpty() || path.startsWith(it.path) }.version
    if (expected > 0 && major > expected) {
      myErrors.add(path + ": .class file version " + major + " exceeds expected " + expected)
    }
  }
}