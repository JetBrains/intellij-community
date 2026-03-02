// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.PosixFilePermissionsUtil
import com.intellij.util.text.nullize
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.BufferedInputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import kotlin.io.path.isDirectory
import kotlin.io.path.name

interface OsSpecificDistributionBuilder {
  companion object {
    @Internal
    fun suffix(arch: JvmArchitecture): String = if (arch == JvmArchitecture.x64) "" else "-${arch.fileSuffix}"
  }

  val targetOs: OsFamily
  val targetLibcImpl: LibcImpl

  suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture)

  suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture)

  suspend fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture): Path

  suspend fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, libc: LibcImpl): Sequence<String> = emptySequence()

  suspend fun generateExecutableFilesMatchers(includeRuntime: Boolean, arch: JvmArchitecture, libc: LibcImpl = targetLibcImpl): Map<PathMatcher, String> {
    val fileSystem = FileSystems.getDefault()
    return generateExecutableFilesPatterns(includeRuntime, arch, libc)
      .distinct()
      .map(FileUtilRt::toSystemIndependentName)
      .associateBy {
        fileSystem.getPathMatcher("glob:$it")
      }
  }

  suspend fun checkExecutablePermissions(distribution: Path, root: String, includeRuntime: Boolean = true, arch: JvmArchitecture, libc: LibcImpl, context: BuildContext) {
    spanBuilder("Permissions check for ${distribution.name}").use {
      val patterns = generateExecutableFilesMatchers(includeRuntime, arch, libc)
      val matchedFiles = when {
        patterns.isEmpty() -> return@use
        SystemInfoRt.isWindows && distribution.isDirectory() -> return@use
        distribution.isDirectory() -> checkDirectory(distribution.resolve(root), patterns.keys)
        "$distribution".endsWith(".tar.gz") -> checkTar(distribution, root, patterns.keys)
        "$distribution".endsWith(".snap") -> checkSnap(distribution, root, patterns.keys, context)
        else -> checkZip(distribution, root, patterns.keys)
      }
      val notValid = matchedFiles.filterNot { it.isValid }
      check(notValid.isEmpty()) {
        "Missing executable permissions in $distribution for:\n" +
        notValid.joinToString(separator = "\n")
      }
      val unmatchedPatterns = patterns.keys - matchedFiles.asSequence()
        .flatMap { it.patterns }
        .toSet()
      if (unmatchedPatterns.isNotEmpty()) {
        context.messages.warning(matchedFiles.joinToString(prefix = "Matched files ${distribution.name}:\n", separator = "\n"))
        unmatchedPatterns.joinToString(prefix = "Unmatched executable permissions patterns in ${distribution.name}: ") {
          patterns.getValue(it)
        }.let { message ->
          if (TeamCityHelper.isUnderTeamCity) {
            context.messages.reportBuildProblem(message)
          }
          else {
            context.messages.warning(message)
          }
        }
      }
    }
  }

  fun writeVmOptions(distBinDir: Path): Path

  /**
   * @return .dmg, .tag.gz, .exe or other distribution files built
   */
  fun distributionFilesBuilt(arch: JvmArchitecture): List<Path>

  fun isRuntimeBundled(file: Path): Boolean
}

private fun checkDirectory(distribution: Path, patterns: Collection<PathMatcher>): List<MatchedFile> {
  return Files.walk(distribution).use { files ->
    files.filter { !Files.isDirectory(it) }.map { file ->
      val relativePath = distribution.relativize(file)
      val matched = patterns.filter { it.matches(relativePath) }
      if (matched.isEmpty()) null
      else {
        MatchedFile(distribution.relativize(file).toString(), OWNER_EXECUTE in Files.getPosixFilePermissions(file), matched)
      }
    }.toList().filterNotNull()
  }
}

private fun checkTar(distribution: Path, root: String, patterns: Collection<PathMatcher>): List<MatchedFile> {
  TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(distribution)))).use { stream ->
    val matched = mutableListOf<MatchedFile>()
    while (true) {
      val entry = stream.nextEntry ?: break
      var entryPath = Path.of(entry.name)
      if (!root.isEmpty()) {
        entryPath = Path.of(root).relativize(entryPath)
      }
      if (!entry.isDirectory) {
        val matchedPatterns = patterns.filter { it.matches(entryPath) }
        if (matchedPatterns.isNotEmpty()) {
          matched.add(MatchedFile(entry.name, OWNER_EXECUTE in PosixFilePermissionsUtil.fromUnixMode(entry.mode), matchedPatterns))
        }
      }
    }
    return matched
  }
}

private fun checkZip(distribution: Path, root: String, patterns: Collection<PathMatcher>): List<MatchedFile> {
  return ZipFile.Builder().setSeekableByteChannel(Files.newByteChannel(distribution)).get().use { zipFile ->
    zipFile.entries.asSequence().filter { !it.isDirectory }.mapNotNull { entry ->
      var entryPath = Path.of(entry.name)
      if (!root.isEmpty()) {
        entryPath = Path.of(root).relativize(entryPath)
      }
      val matched = patterns.filter { it.matches(entryPath) }
      if (matched.isEmpty()) null
      else {
        MatchedFile(entry.name, OWNER_EXECUTE in PosixFilePermissionsUtil.fromUnixMode(entry.unixMode), matched)
      }
    }.toList()
  }
}

private class MatchedFile(val relativePath: String, val isValid: Boolean, val patterns: Collection<PathMatcher>) {
  override fun toString() = relativePath
}

private suspend fun checkSnap(distribution: Path, root: String, patterns: Collection<PathMatcher>, context: BuildContext): List<MatchedFile> {
  val stdout = ArrayList<String>()
  val extractionRoot = "ROOT"
  runProcess(
    args = listOf("unsquashfs", "-llnumeric", "-dest", extractionRoot, "$distribution"),
    inheritOut = false,
    stdOutConsumer = { s ->
      s.nullize()?.trim()?.let { stdout.add(it) }
    },
    stdErrConsumer = context.messages::warning,
  )

  val matched = mutableListOf<MatchedFile>()
  val extractionPrefix = "$extractionRoot/"

  for (line in stdout) {
    if (line.isEmpty()) continue
    if (line[0] == 'd') continue // directory
    if (line[0] == 'l') continue // symlink
    if (line[0] != '-') continue // regular file
    // `-rw-r--r-- 0/0                    1820 2025-03-11 15:19 ROOT/Install-Linux-tar.txt`
    val i = line.indexOf(extractionPrefix)
    if (i == -1) continue // preamble
    val path = line.substring(i + extractionPrefix.length)
    var entryPath = Path.of(path)
    if (!root.isEmpty()) {
      entryPath = Path.of(root).relativize(entryPath)
    }
    val matchedPatterns = patterns.filter { it.matches(entryPath) }
    if (matchedPatterns.isNotEmpty()) {
      matched.add(MatchedFile(relativePath = path, isValid = line.startsWith("-rwx"), patterns = matchedPatterns))
    }
  }

  return matched
}
