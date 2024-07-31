// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.telemetry.use
import com.intellij.util.io.PosixFilePermissionsUtil
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.BufferedInputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import kotlin.io.path.isDirectory
import kotlin.io.path.name

interface OsSpecificDistributionBuilder {
  val context: BuildContext
  val targetOs: OsFamily

  suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture)

  suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture)

  fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture)

  fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> = emptyList()

  fun generateExecutableFilesMatchers(includeRuntime: Boolean, arch: JvmArchitecture): Map<PathMatcher, String> {
    val fileSystem = FileSystems.getDefault()
    return generateExecutableFilesPatterns(includeRuntime, arch)
      .asSequence().distinct()
      .map(FileUtil::toSystemIndependentName)
      .associateBy {
        fileSystem.getPathMatcher("glob:$it")
      }
  }

  fun checkExecutablePermissions(distribution: Path, root: String, includeRuntime: Boolean = true, arch: JvmArchitecture) {
    TraceManager.spanBuilder("Permissions check for ${distribution.name}").use {
      val patterns = generateExecutableFilesMatchers(includeRuntime, arch)
      val matchedFiles = when {
        patterns.isEmpty() -> return
        SystemInfoRt.isWindows && distribution.isDirectory() -> return
        distribution.isDirectory() -> checkDirectory(distribution.resolve(root), patterns.keys)
        "$distribution".endsWith(".tar.gz") -> checkTar(distribution, root, patterns.keys)
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
        if (TeamCityHelper.isUnderTeamCity) {
          context.messages.reportBuildProblem(
            unmatchedPatterns.joinToString(prefix = "Unmatched executable permissions patterns in ${distribution.name}: ") {
              patterns.getValue(it)
            }
          )
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

  private class MatchedFile(val relativePath: String, val isValid: Boolean, val patterns: Collection<PathMatcher>) {
    override fun toString() = relativePath
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
        val entry = (stream.nextEntry ?: break) as TarArchiveEntry
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
    return ZipFile(Files.newByteChannel(distribution)).use { zipFile ->
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

  companion object {
    @Internal
    fun suffix(arch: JvmArchitecture): String = when (arch) {
      JvmArchitecture.x64 -> ""
      else -> "-${arch.fileSuffix}"
    }
  }
}
