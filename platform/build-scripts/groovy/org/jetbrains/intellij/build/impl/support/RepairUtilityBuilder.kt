// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.support

import com.intellij.openapi.util.SystemInfoRt
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.BuildOptions.Companion.REPAIR_UTILITY_BUNDLE_STEP
import org.jetbrains.intellij.build.JvmArchitecture.Companion.currentJvmArch
import org.jetbrains.intellij.build.OsFamily.Companion.currentOs
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.io.runProcess

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import java.util.*

import java.nio.file.attribute.PosixFilePermission.*

/**
 * Builds 'repair' command line utility which is a simple and automated way to fix the IDE when it cannot start:
 * <ul>
 * <li>generates or checks IDE installation integrity</li>
 * <li>checks the idea.log file for errors</li>
 * <li>checks if broken, or old plugins are installed</li>
 * <li>checks the runtime that starts IDE</li>
 * <li>checks for problems in .vmoptions file used to start the IDE</li>
 * </ul>
 *
 * <p>
 * Note: Bash and Docker are required to build the utility.
 * </p>
 */
class RepairUtilityBuilder {
  companion object {
    @Volatile
    private lateinit var _binariesCache: Map<Binary, Path>

    private fun getBinariesCacheOrSetIfNull(context: BuildContext): Map<Binary, Path> {
      if (!::_binariesCache.isInitialized) {
        _binariesCache = buildBinaries(context)
      }
      return _binariesCache
    }

    private val BINARIES: Collection<Binary> = listOf(
      Binary(OsFamily.LINUX, JvmArchitecture.x64, "bin/repair-linux-amd64", "bin/repair"),
      Binary(OsFamily.LINUX, JvmArchitecture.aarch64, "bin/repair-linux-arm64", "bin/repair"),
      Binary(OsFamily.WINDOWS, JvmArchitecture.x64, "bin/repair.exe", "bin/repair.exe"),
      Binary(OsFamily.MACOS, JvmArchitecture.x64, "bin/repair-darwin-amd64", "bin/repair"),
      Binary(OsFamily.MACOS, JvmArchitecture.aarch64, "bin/repair-darwin-arm64", "bin/repair")
    )

    class Binary(val os: OsFamily, val arch: JvmArchitecture, val relativeSourcePath: String, val relativeTargetPath: String)

    @Synchronized
    fun bundle(context: BuildContext, os: OsFamily, arch: JvmArchitecture, distributionDir: Path) {
      context.executeStep(
        spanBuilder("bundle repair-utility")
          .setAttribute("os", os.osName),
        REPAIR_UTILITY_BUNDLE_STEP) {
        val cache = getBinariesCacheOrSetIfNull(context)
        if (cache.isEmpty()) {
          return@executeStep
        }

        val binary = findBinary(context, os, arch)
        val path = cache[binary]
        if (path == null) {
          context.messages.error("No binary was built for $os and $arch")
          return@executeStep
        }

        val repairUtilityTarget = distributionDir.resolve(binary!!.relativeTargetPath)
        Span.current().addEvent("copy $path to $repairUtilityTarget")
        Files.createDirectories(repairUtilityTarget.parent)
        Files.copy(path, repairUtilityTarget, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        return@executeStep
      }
    }

    @Synchronized
    fun generateManifest(context: BuildContext, unpackedDistribution: Path, manifestFileNamePrefix: String) {
      context.executeStep(spanBuilder("generate installation integrity manifest")
                            .setAttribute("dir", unpackedDistribution.toString()), REPAIR_UTILITY_BUNDLE_STEP) {
        if (Files.notExists(unpackedDistribution)) {
          context.messages.error("$unpackedDistribution doesn't exist")
        }

        val cache = getBinariesCacheOrSetIfNull(context)
        if (cache.isEmpty()) {
          return@executeStep
        }
        val binary = findBinary(context, currentOs, currentJvmArch)
        val binaryPath = repairUtilityProjectHome(context)!!.resolve(binary!!.relativeSourcePath)
        val tmpDir = context.paths.tempDir.resolve(REPAIR_UTILITY_BUNDLE_STEP + UUID.randomUUID().toString())
        Files.createDirectories(tmpDir)
        try {
          runProcess(listOf(binaryPath.toString(), "hashes", "-g", "--path", unpackedDistribution.toString()), tmpDir, context.messages)
        }
        catch (e: Throwable) {
          context.messages.warning("Manifest generation failed, listing unpacked distribution content for debug:")
          Files.walk(unpackedDistribution).use { files ->
            files.forEach { filePath: Path ->
              context.messages.warning(filePath.toString())
            }
          }
          throw e
        }

        val manifest = tmpDir.resolve("manifest.json")
        if (Files.notExists(manifest)) {
          val repairLog = tmpDir.resolve("repair.log")
          context.messages.error("Unable to generate installation integrity manifest: ${Files.readString(repairLog)}")
        }

        val artifact = context.paths.artifactDir.resolve("${manifestFileNamePrefix}.manifest")
        Files.move(manifest, artifact, StandardCopyOption.REPLACE_EXISTING)
        return@executeStep
      }
    }

    @Synchronized
    fun binaryFor(buildContext: BuildContext, os: OsFamily, arch: JvmArchitecture): Binary? {
      if (!buildContext.options.buildStepsToSkip.contains(REPAIR_UTILITY_BUNDLE_STEP)) {
        val cache = getBinariesCacheOrSetIfNull(buildContext)
        if (!cache.isEmpty()) {
          return findBinary(buildContext, os, arch)
        }
      }
      return null
    }

    private fun findBinary(buildContext: BuildContext, os: OsFamily, arch: JvmArchitecture): Binary? {
      val binary = BINARIES.find { it.os == os && it.arch == arch }
      if (binary == null) {
        buildContext.messages.error("Unsupported binary: $os $arch")
      }
      return binary
    }

    private fun repairUtilityProjectHome(buildContext: BuildContext): Path? {
      val projectHome = buildContext.paths.communityHomeDir.communityRoot.parent.resolve("build/support/repair-utility")
      if (Files.notExists(projectHome)) {
        buildContext.messages.warning("$projectHome doesn't exist")
        return null
      }
      return projectHome
    }

    private fun buildBinaries(buildContext: BuildContext): Map<Binary, Path> {
      return buildContext.messages.block("build repair-utility") {
        if (SystemInfoRt.isWindows) {
          // FIXME: Linux containers on Windows should be fine
          buildContext.messages.warning("Cannot be built on Windows")
          return@block emptyMap()
        }

        val projectHome = repairUtilityProjectHome(buildContext)
        if (projectHome == null) {
          return@block emptyMap()
        }
        else {
          try {
            runProcess(listOf("docker", "--version"), null, buildContext.messages)
            runProcess(listOf("bash", "build.sh"), projectHome, buildContext.messages)
          }
          catch (e: Throwable) {
            if (TeamCityHelper.isUnderTeamCity) {
              throw e
            }
            return@block emptyMap<Binary, Path>()
          }

          val binaries: Map<Binary, Path> = BINARIES.associateWith { projectHome.resolve(it.relativeSourcePath) }
          val executablePermissions = setOf(
            OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE
          )
          for (file in binaries.values) {
            if (Files.notExists(file)) {
              buildContext.messages.error("$file doesn't exist")
            }
            Files.setPosixFilePermissions(file, executablePermissions)
          }
          return@block binaries
        }
      }
    }
  }
}
