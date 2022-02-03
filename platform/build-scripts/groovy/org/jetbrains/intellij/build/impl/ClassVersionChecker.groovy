// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.nio.channels.FileChannel
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicInteger
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

    volatile boolean wasUsed

    Rule(String path, int version) {
      this.path = path
      this.version = version
    }
  }

  private final List<Rule> rules
  private AtomicInteger checkedJarCount = new AtomicInteger()
  private AtomicInteger checkedClassCount = new AtomicInteger()

  private ClassVersionChecker(List<Rule> rules) {
    this.rules = rules
  }

  static int classVersion(String version) {
    return version.isEmpty() ? -1 : JavaVersion.parse(version).feature + 44  // 1.1 = 45
  }

  static void checkVersions(Map<String, String> config, BuildContext context, Path root) {
    if (context.options.buildStepsToSkip.contains(BuildOptions.VERIFY_CLASS_FILE_VERSIONS)) {
      return
    }

    BuildHelper.getInstance(context).span(TracerManager.spanBuilder("verify class file versions")
                                            .setAttribute("ruleCount", config.size())
                                            .setAttribute("root", root.toString()), new Runnable() {
      @Override
      void run() {
        List<Rule> rules = new ArrayList<Rule>(config.size())
        for (Map.Entry<String, String> entry : config.entrySet()) {
          rules.add(new Rule(entry.key, classVersion(entry.value)))
        }
        rules.sort(new Comparator<Rule>() {
          @Override
          int compare(Rule o1, Rule o2) {
            return Integer.compare(-o1.path.length(), -o2.path.length())
          }
        })
        if (rules.isEmpty() || !rules.last().path.isEmpty()) {
          throw new IllegalArgumentException("Invalid configuration: missing default version")
        }

        ClassVersionChecker checker = new ClassVersionChecker(rules)
        Collection<String> errors = new ConcurrentLinkedQueue<>()
        if (Files.isDirectory(root)) {
          checker.visitDirectory(root, "", errors)
        }
        else {
          checker.visitFile(root, "", errors)
        }

        if (checker.checkedClassCount.get() == 0) {
          context.messages.error("No classes found under $root - please check the configuration")
        }

        int errorCount = errors.size()
        Span.current()
          .setAttribute("checkedClasses", checker.checkedClassCount.get())
          .setAttribute("checkedJarCount", checker.checkedJarCount.get())
          .setAttribute("errorCount", errorCount)
        if (errorCount != 0) {
          for (String error in errors) {
            context.messages.warning(error)
          }
          context.messages.error("Failed with $errorCount problems")
        }

        Collection<String> unusedRules = rules.findResults { it.wasUsed ? null : it.path }
        if (!unusedRules.isEmpty()) {
          context.messages.error("Class version check rules for the following paths don't match any files, probably entries in " +
                                 "ProductProperties::versionCheckerConfig are out of date:\n${String.join("\n", unusedRules)}")
        }
      }
    })
  }

  private void visitDirectory(Path directory, String relPath, Collection<String> errors) {
    List<ForkJoinTask<?>> tasks = new ArrayList<>()
    DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)
    try {
      // closure must be used, otherwise variables are not captured by FJT
      dirStream.each { Path child ->
        if (Files.isDirectory(child)) {
          tasks.add(ForkJoinTask.adapt(new Runnable() {
            @Override
            void run() {
              visitDirectory(child, join(relPath, '/', child.fileName.toString()), errors)
            }
          }))
        }
        else {
          tasks.add(ForkJoinTask.adapt(new Runnable() {
            @Override
            void run() {
              visitFile(child, join(relPath, '/', child.fileName.toString()), errors)
            }
          }))
        }
      }
    }
    finally {
      dirStream.close()
    }

    ForkJoinTask.invokeAll(tasks)
  }

  private void visitFile(@NotNull Path file, String relPath, Collection<String> errors) {
    String fullPath = file.toString()
    if (fullPath.endsWith(".zip") || fullPath.endsWith(".jar")) {
      visitZip(fullPath, relPath, new ZipFile(FileChannel.open(file, EnumSet.of(StandardOpenOption.READ))), errors)
    }
    else if (fullPath.endsWith(".class") && !fullPath.endsWith("module-info.class") && !isMultiVersion(fullPath)) {
      new BufferedInputStream(Files.newInputStream(file)).withCloseable { checkVersion(relPath, it, errors) }
    }
  }

  private static boolean isMultiVersion(String path) {
    return path.startsWith("META-INF/versions/") || path.contains("/META-INF/versions/") || (SystemInfoRt.isWindows && path.contains("\\META-INF\\versions\\"))
  }

  // use ZipFile - avoid a lot of small lookups to read entry headers (ZipFile uses central directory)
  private void visitZip(String zipPath, String zipRelPath, ZipFile file, Collection<String> errors) {
    try {
      checkedJarCount.incrementAndGet()
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
            visitZip(childZipPath, join(zipRelPath, "!/", name), new ZipFile(new SeekableInMemoryByteChannel(file.getInputStream(entry).readAllBytes())), errors)
          }
          catch (ZipException e) {
            throw new RuntimeException("Cannot read " + childZipPath, e)
          }
        }
        else if (name.endsWith(".class") && !name.endsWith("module-info.class") && !isMultiVersion(name)) {
          checkVersion(join(zipRelPath, "!/", name), file.getInputStream(entry), errors)
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

  private void checkVersion(String path, InputStream stream, Collection<String> errors) {
    checkedClassCount.incrementAndGet()

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

    Rule rule = rules.find { it.path.isEmpty() || path.startsWith(it.path) }
    rule.wasUsed = true
    int expected = rule.version
    if (expected > 0 && major > expected) {
      errors.add(path + ": .class file version " + major + " exceeds expected " + expected)
    }
  }
}
