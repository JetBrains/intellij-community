// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
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
internal suspend fun checkClassFiles(versionCheckConfig: Map<String, String>,
                                     forbiddenSubPaths: List<String>,
                                     root: Path,
                                     messages: BuildMessages) {
  spanBuilder("verify class files")
    .setAttribute("ruleCount", versionCheckConfig.size.toLong())
    .setAttribute("forbiddenSubPathCount", forbiddenSubPaths.size.toLong())
    .setAttribute("root", root.toString())
    .useWithScope2 {
      val rules = ArrayList<Rule>(versionCheckConfig.size)
      for (entry in versionCheckConfig.entries) {
        rules.add(Rule(path = entry.key, version = classVersion(entry.value)))
      }
      rules.sortWith { o1, o2 -> (-o1.path.length).compareTo(-o2.path.length) }
      check(rules.isEmpty() || rules.last().path.isEmpty()) {
        throw ClassFileCheckError("Invalid configuration: missing default version $rules")
      }

      val defaultVersion = rules.lastOrNull()?.version
      check(defaultVersion == null || rules.dropLast(1).none { it.version == defaultVersion }) {
        throw ClassFileCheckError("Redundant rules with default version: " + rules.dropLast(1).filter {
          it.version == defaultVersion
        })
      }

      val checker = ClassFileChecker(rules, forbiddenSubPaths)
      val errors = ConcurrentLinkedQueue<String>()
      if (Files.isDirectory(root)) {
        coroutineScope {
          checker.apply {
            visitDirectory(root, "", errors)
          }
        }
      }
      else {
        checker.visitFile(root, "", errors)
      }

      check(rules.isEmpty() || checker.checkedClassCount.get() != 0) {
        throw ClassFileCheckError("No classes found under $root - please check the configuration")
      }

      val errorCount = errors.size
      Span.current()
        .setAttribute("checkedClasses", checker.checkedClassCount.get().toLong())
        .setAttribute("checkedJarCount", checker.checkedJarCount.get().toLong())
        .setAttribute("errorCount", errorCount.toLong())
      for (error in errors) {
        messages.warning("---\n$error")
      }
      check(errorCount == 0) {
        throw ClassFileCheckError("Failed with $errorCount problems", errors)
      }

      val unusedRules = rules.filter { !it.wasUsed }
      check(unusedRules.isEmpty()) {
        throw ClassFileCheckError("Class version check rules for the following paths don't match any files, probably entries in " +
                                  "ProductProperties::versionCheckerConfig are out of date:\n${unusedRules.joinToString(separator = "\n")}")
      }
    }
}

class ClassFileCheckError(message: String, val errors: Collection<String> = emptyList()) : Exception(message)

private val READ = EnumSet.of(StandardOpenOption.READ)

private class ClassFileChecker(private val versionRules: List<Rule>, private val forbiddenSubPaths: List<String>) {
  val checkedJarCount = AtomicInteger()
  val checkedClassCount = AtomicInteger()

  fun CoroutineScope.visitDirectory(directory: Path, relPath: String, errors: MutableCollection<String>) {
    Files.newDirectoryStream(directory).use { dirStream ->
      // closure must be used, otherwise variables are not captured by FJT
      for (child in dirStream) {
        if (Files.isDirectory(child)) {
          launch {
            visitDirectory(directory = child, relPath = join(relPath, "/", child.fileName.toString()), errors = errors)
          }
        }
        else {
          launch {
            visitFile(file = child, relPath = join(relPath, "/", child.fileName.toString()), errors = errors)
          }
        }
      }
    }
  }

  fun visitFile(file: Path, relPath: String, errors: MutableCollection<String>) {
    val fullPath = file.toString()
    if (fullPath.endsWith(".zip") || fullPath.endsWith(".jar")) {
      visitZip(fullPath, relPath, ZipFile(FileChannel.open(file, READ)), errors)
    }
    else if (fullPath.endsWith(".class")) {
      checkIfSubPathIsForbidden(relPath, errors)

      val contentCheckRequired = versionRules.isNotEmpty() && !fullPath.endsWith("module-info.class") && !isMultiVersion(fullPath)
      if (contentCheckRequired) {
        BufferedInputStream(Files.newInputStream(file)).use { checkVersion(relPath, it, errors) }
      }
    }
  }

  // use ZipFile - avoid a lot of small lookups to read entry headers (ZipFile uses central directory)
  private fun visitZip(zipPath: String, zipRelPath: String, file: ZipFile, errors: MutableCollection<String>) {
    file.use {
      checkedJarCount.incrementAndGet()
      val entries = file.entries
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.isDirectory) {
          continue
        }

        val name = entry.name
        if (name.endsWith(".zip") || name.endsWith(".jar")) {
          val childZipPath = "$zipPath!/$name"
          try {
            visitZip(zipPath = childZipPath,
                     zipRelPath = join(zipRelPath, "!/", name),
                     file = ZipFile(SeekableInMemoryByteChannel(file.getInputStream(entry).readAllBytes())),
                     errors = errors)
          }
          catch (e: ZipException) {
            throw RuntimeException("Cannot read $childZipPath", e)
          }
        }
        else if (name.endsWith(".class")) {
          val relPath = join(zipRelPath, "!/", name)

          checkIfSubPathIsForbidden(relPath, errors)

          val contentCheckRequired = versionRules.isNotEmpty() && !name.endsWith("module-info.class") && !isMultiVersion(name)
          if (contentCheckRequired) {
            checkVersion(relPath, file.getInputStream(entry), errors)
          }
        }
      }
    }
  }

  private fun checkIfSubPathIsForbidden(relPath: String, errors: MutableCollection<String>) {
    for (f in forbiddenSubPaths) {
      if (relPath.contains(f)) {
        errors.add("$relPath: .class file has a forbidden sub-path: $f")
      }
    }
  }

  private fun checkVersion(path: String, stream: InputStream, errors: MutableCollection<String>) {
    checkedClassCount.incrementAndGet()

    val dataStream = DataInputStream(stream)
    if (dataStream.readInt() != 0xCAFEBABE.toInt() || dataStream.skipBytes(3) != 3) {
      errors.add("$path: invalid .class file header")
      return
    }

    val major = dataStream.readUnsignedByte()
    if (major < 44 || major >= 100) {
      errors.add("$path: suspicious .class file version: $major")
      return
    }

    val rule = versionRules.first { it.path.isEmpty() || path.startsWith(it.path) }
    rule.wasUsed = true
    val expected = rule.version
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    if (expected > 0 && major > expected) {
      errors.add("$path: .class file version $major exceeds expected $expected")
    }
  }
}

private fun isMultiVersion(path: String): Boolean {
  return path.startsWith("META-INF/versions/") ||
         path.contains("/META-INF/versions/") ||
         (SystemInfoRt.isWindows && path.contains("\\META-INF\\versions\\"))
}

private fun classVersion(version: String) = if (version.isEmpty()) -1 else JavaVersion.parse(version).feature + 44  // 1.1 = 45

private fun join(prefix: String, separator: String, suffix: String) = if (prefix.isEmpty()) suffix else (prefix + separator + suffix)

private data class Rule(@JvmField val path: String, @JvmField val version: Int) {
  @Volatile
  @JvmField
  var wasUsed = false
}
