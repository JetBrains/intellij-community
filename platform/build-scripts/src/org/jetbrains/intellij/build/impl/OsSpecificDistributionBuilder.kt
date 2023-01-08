// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.util.io.PosixFilePermissionsUtil
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TraceManager
import java.io.BufferedInputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.name

interface OsSpecificDistributionBuilder {
  val context: BuildContext
  val targetOs: OsFamily

  suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture)

  suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture)

  fun generateExecutableFilesPatterns(includeRuntime: Boolean): List<String> = emptyList()

  fun checkExecutablePermissions(distribution: Path, root: String, includeRuntime: Boolean = true) {
    TraceManager.spanBuilder("Permissions check for ${distribution.name}").useWithScope {
      val executableFilesPatterns = generateExecutableFilesPatterns(includeRuntime)
      val patterns = executableFilesPatterns.map {
        FileSystems.getDefault().getPathMatcher("glob:$it")
      }
      try {
        val entries = when {
          patterns.isEmpty() -> return
          Files.isDirectory(distribution) -> checkDirectory(distribution.resolve(root), patterns)
          "$distribution".endsWith(".tar.gz") -> checkTar(distribution, root, patterns)
          else -> checkZip(distribution, root, patterns)
        }
        if (entries.isNotEmpty()) {
          context.messages.error("Missing executable permissions in $distribution for:\n" + entries.joinToString(separator = "\n"))
        }
      }
      catch (e: MissingFilesException) {
        context.messages.error("Executable files patterns:\n" +
                               executableFilesPatterns.joinToString(separator = "\n") +
                               "\nFound files:\n" +
                               e.found.joinToString(separator = "\n"))
      }
    }
  }

  private fun checkDirectory(distribution: Path, patterns: List<PathMatcher>): List<String> {
    val entries = Files.walk(distribution).use { files ->
      val found = files.filter { file ->
        val relativePath = distribution.relativize(file)
        !Files.isDirectory(file) && patterns.any {
          it.matches(relativePath)
        }
      }.toList()
      if (found.size < patterns.size) {
        throw MissingFilesException(found)
      }
      found.stream()
        .filter { PosixFilePermission.OWNER_EXECUTE !in Files.getPosixFilePermissions(it) }
        .map { distribution.relativize(it).toString() }
        .toList()
    }
    return entries
  }

  private fun checkTar(distribution: Path, root: String, patterns: List<PathMatcher>) =
    TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(distribution)))).use { stream ->
      val found = mutableListOf<TarArchiveEntry>()
      while (true) {
        val entry = (stream.nextEntry ?: break) as TarArchiveEntry
        var entryPath = Path.of(entry.name)
        if (!root.isEmpty()) {
          entryPath = Path.of(root).relativize(entryPath)
        }
        if (!entry.isDirectory && patterns.any { it.matches(entryPath) }) {
          found.add(entry)
        }
      }
      if (found.size < patterns.size) {
        throw MissingFilesException(found)
      }
      found
        .filter { PosixFilePermission.OWNER_EXECUTE !in PosixFilePermissionsUtil.fromUnixMode(it.mode) }
        .map { "${it.name}: mode is 0${Integer.toOctalString(it.mode)}" }
    }


  private fun checkZip(distribution: Path, root: String, patterns: List<PathMatcher>) =
    ZipFile(Files.newByteChannel(distribution)).use { zipFile ->
      val found = zipFile.entries.asSequence().filter { entry ->
        var entryPath = Path.of(entry.name)
        if (!root.isEmpty()) {
          entryPath = Path.of(root).relativize(entryPath)
        }
        !entry.isDirectory && patterns.any { it.matches(entryPath) }
      }.toList()
      if (found.size < patterns.size) {
        throw MissingFilesException(found)
      }
      found
        .filter { entry -> PosixFilePermission.OWNER_EXECUTE !in PosixFilePermissionsUtil.fromUnixMode(entry.unixMode) }
        .map { "${it.name}: mode is 0${Integer.toOctalString(it.unixMode)}" }
    }

  private class MissingFilesException(val found: List<Any>) : Exception()
}