// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.lang.JavaVersion
import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipException

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
final class ClassVersionChecker {
  private static final class Rule {
    final String path
    final int version

    Rule(String path, int version) {
      this.path = path
      this.version = version
    }
  }

  private final List<Rule> myRules
  private final Set<Rule> myUsedRules = new HashSet<>()
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

  void checkVersions(BuildContext buildContext, Path root) {
    buildContext.messages.block("Verifying class file versions") {
      myJars = 0
      myClasses = 0
      myErrors = []

      buildContext.messages.info("Checking with ${myRules.size()} rules in ${root} ...")
      if (Files.isDirectory(root)) {
        visitDirectory(root, "")
      }
      else {
        visitFile(root, "")
      }

      if (myClasses == 0) {
        buildContext.messages.error("No classes found under ${root} - please check the configuration")
      }
      buildContext.messages.info("Done, checked ${myClasses} classes in ${myJars} JARs")
      if (!myErrors.isEmpty()) {
        myErrors.each { buildContext.messages.warning(it) }
        buildContext.messages.error("Failed with ${myErrors.size()} problems")
      }

      List<Rule> unusedRules = myRules - myUsedRules
      if (!unusedRules.isEmpty()) {
        buildContext.messages.error("Class version check rules for the following paths don't match any files, probably entries in " +
                                    "ProductProperties::versionCheckerConfig are incorrect:\n" +
                                    "${unusedRules.collect {it.path}.join("\n")}")
      }
    }
  }

  private void visitDirectory(Path directory, String relPath) {
    DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)
    try {
      for (Path child in dirStream) {
        if (Files.isDirectory(child)) {
          visitDirectory(child, join(relPath, '/', child.fileName.toString()))
        }
        else {
          visitFile(child, join(relPath, '/', child.fileName.toString()))
        }
      }
    }
    finally {
      dirStream.close()
    }
  }

  private void visitFile(@NotNull Path file, String relPath) {
    String fullPath = file.toString()
    if (fullPath.endsWith(".zip") || fullPath.endsWith(".jar")) {
      visitZip(fullPath, relPath, new ZipFile(Files.newByteChannel(file, EnumSet.of(StandardOpenOption.READ))))
    }
    else if (fullPath.endsWith(".class") && !fullPath.endsWith("module-info.class") && !isMultiVersion(fullPath)) {
      new BufferedInputStream(Files.newInputStream(file)).withCloseable { checkVersion(relPath, it) }
    }
  }

  private static boolean isMultiVersion(String path) {
    return path.startsWith("META-INF/versions/") || path.contains("/META-INF/versions/") || (SystemInfoRt.isWindows && path.contains("\\META-INF\\versions\\"))
  }

  // use ZipFile - avoid a lot of small lookups to read entry headers (ZipFile uses central directory)
  private void visitZip(String zipPath, String zipRelPath, ZipFile file) {
    try {
      myJars++
      Enumeration<ZipArchiveEntry> entries = file.entries
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement()
        if (entry.isDirectory()) {
          continue
        }

        String name = entry.name
        if (name.endsWith(".zip") || name.endsWith(".jar")) {
          String childZipPath = zipPath + "!/" + name
          try {
            visitZip(childZipPath, join(zipRelPath, "!/", name), new ZipFile(new SeekableInMemoryByteChannel(file.getInputStream(entry).readAllBytes())))
          }
          catch (ZipException e) {
            throw new RuntimeException("Cannot read " + childZipPath, e)
          }
        }
        else if (name.endsWith(".class") && !name.endsWith("module-info.class") && !isMultiVersion(name)) {
          checkVersion(join(zipRelPath, "!/", name), file.getInputStream(entry))
        }
      }
    }
    finally {
      file.close()
    }
  }

  private static String join(String prefix, String separator, String suffix) {
    return prefix.isEmpty() ? suffix : (prefix + separator + suffix)
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

    def rule = myRules.find { it.path.isEmpty() || path.startsWith(it.path) }
    myUsedRules.add(rule)
    int expected = rule.version
    if (expected > 0 && major > expected) {
      myErrors.add(path + ": .class file version " + major + " exceeds expected " + expected)
    }
  }
}