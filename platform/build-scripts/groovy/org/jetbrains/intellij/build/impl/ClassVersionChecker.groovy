// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.lang.JavaVersion
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinTask
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
  private int checkedJarCount, checkedClassCount
  private Collection<String> errors

  ClassVersionChecker(Map<String, String> config) {
    myRules = config.entrySet().collect { new Rule(it.key, classVersion(it.value)) }.sort { -it.path.length() }.toList()
    if (myRules.isEmpty() || !myRules.last().path.isEmpty()) {
      throw new IllegalArgumentException("Invalid configuration: missing default version")
    }
  }

  static int classVersion(String version) {
    return version.isEmpty() ? -1 : JavaVersion.parse(version).feature + 44  // 1.1 = 45
  }

  void checkVersions(BuildContext context, Path root) {
    if (context.options.buildStepsToSkip.contains(BuildOptions.VERIFY_CLASS_FILE_VERSIONS)) {
      return
    }

    BuildHelper.getInstance(context).span(TracerManager.spanBuilder("verify class file versions")
                                            .setAttribute("ruleCount", myRules.size())
                                            .setAttribute("root", root.toString()), new Runnable() {
      @Override
      void run() {
        checkedJarCount = 0
        checkedClassCount = 0
        errors = new ConcurrentLinkedQueue<>()
        if (Files.isDirectory(root)) {
          visitDirectory(root, "")
        }
        else {
          visitFile(root, "")
        }

        if (checkedClassCount == 0) {
          context.messages.error("No classes found under $root - please check the configuration")
        }

        int errorCount = errors.size()
        Span.current()
          .setAttribute("checkedClasses", checkedClassCount)
          .setAttribute("checkedJarCount", checkedJarCount)
          .setAttribute("errorCount", errorCount)
        if (errorCount != 0) {
          for (String error in errors) {
            context.messages.warning(error)
          }
          context.messages.error("Failed with $errorCount problems")
        }

        List<Rule> unusedRules = myRules - myUsedRules
        if (!unusedRules.isEmpty()) {
          context.messages.error("Class version check rules for the following paths don't match any files, probably entries in " +
                                 "ProductProperties::versionCheckerConfig are out of date:\n${String.join("\n", unusedRules.collect { it.path })}")
        }
      }
    })
  }

  private void visitDirectory(Path directory, String relPath) {
    List<ForkJoinTask<?>> tasks = new ArrayList<>()
    DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)
    try {
      for (Path child in dirStream) {
        if (Files.isDirectory(child)) {
          tasks.add(createVisitDirectory(child, join(relPath, '/', child.fileName.toString())))
        }
        else {
          tasks.add(createVisitFileTask(child, join(relPath, '/', child.fileName.toString())))
        }
      }
    }
    finally {
      dirStream.close()
    }

    ForkJoinTask.invokeAll(tasks)
  }

  private ForkJoinTask<?> createVisitDirectory(Path directory, String relPath) {
    return ForkJoinTask.adapt {
      visitDirectory(directory, relPath)
    }
  }

  private ForkJoinTask<?> createVisitFileTask(Path file, String relPath) {
    return ForkJoinTask.adapt {
      visitFile(file, relPath)
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
      checkedJarCount++
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
    checkedClassCount++

    DataInputStream dataStream = new DataInputStream(stream)
    if (dataStream.readInt() != (int)0xCAFEBABE || dataStream.skipBytes(3) != 3) {
      errors.add(path + ": invalid .class file header")
      return
    }

    int major = dataStream.readUnsignedByte()
    if (major == 196653) major = 45
    if (major < 44 || major >= 100) {
      errors.add(path + ": suspicious .class file version: " + major)
      return
    }

    Rule rule = myRules.find { it.path.isEmpty() || path.startsWith(it.path) }
    myUsedRules.add(rule)
    int expected = rule.version
    if (expected > 0 && major > expected) {
      errors.add(path + ": .class file version " + major + " exceeds expected " + expected)
    }
  }
}
