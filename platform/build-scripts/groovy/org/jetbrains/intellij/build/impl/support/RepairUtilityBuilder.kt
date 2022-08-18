// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.support

import com.intellij.openapi.util.SystemInfoRt
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions.Companion.REPAIR_UTILITY_BUNDLE_STEP
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.JvmArchitecture.Companion.currentJvmArch
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.OsFamily.Companion.currentOs
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.io.runProcess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission.*
import java.util.*

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
    private lateinit var binariesCache: Map<Binary, Path>

    private fun getBinariesCacheOrSetIfNull(context: BuildContext): Map<Binary, Path> {
      if (!::binariesCache.isInitialized) {
        binariesCache = buildBinaries(context)
      }
      return binariesCache
    }

    private val BINARIES: Collection<Binary> = listOf(
      Binary(OsFamily.LINUX, JvmArchitecture.x64, "bin/repair-linux-amd64", "bin/repair", "linux_amd64"),
      Binary(OsFamily.LINUX, JvmArchitecture.aarch64, "bin/repair-linux-arm64", "bin/repair", "linux_arm64"),
      Binary(OsFamily.WINDOWS, JvmArchitecture.x64, "bin/repair.exe", "bin/repair.exe", "windows_amd64"),
      Binary(OsFamily.MACOS, JvmArchitecture.x64, "bin/repair-darwin-amd64", "bin/repair", "darwin_amd64"),
      Binary(OsFamily.MACOS, JvmArchitecture.aarch64, "bin/repair-darwin-arm64", "bin/repair", "darwin_arm64")
    )

    class Binary(
      val os: OsFamily, val arch: JvmArchitecture,
      val relativeSourcePath: String, val relativeTargetPath: String,
      val distributionUrlVariable: String
    ) {
      val distributionSuffix: String
        get() = when (arch) {
                  JvmArchitecture.x64 -> ""
                  JvmArchitecture.aarch64 -> "-" + arch.fileSuffix
                } + when (os) {
                  OsFamily.LINUX -> ".tar.gz"
                  OsFamily.MACOS -> ".dmg"
                  OsFamily.WINDOWS -> ".exe"
                }
    }

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
        require(path != null && binary != null) {
          "No binary was built for $os and $arch"
        }
        val repairUtilityTarget = distributionDir.resolve(binary.relativeTargetPath)
        Span.current().addEvent("copy $path to $repairUtilityTarget")
        Files.createDirectories(repairUtilityTarget.parent)
        Files.copy(path, repairUtilityTarget, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        return@executeStep
      }
    }

    @Synchronized
    fun generateManifest(context: BuildContext, unpackedDistribution: Path, os: OsFamily, arch: JvmArchitecture) {
      context.executeStep(spanBuilder("generate installation integrity manifest")
                            .setAttribute("dir", unpackedDistribution.toString()), REPAIR_UTILITY_BUNDLE_STEP) {
        if (Files.notExists(unpackedDistribution)) {
          context.messages.error("$unpackedDistribution doesn't exist")
        }

        val cache = getBinariesCacheOrSetIfNull(context)
        if (cache.isEmpty()) {
          return@executeStep
        }
        val manifestGenerator = findBinary(context, currentOs, currentJvmArch)
        val distributionBinary = findBinary(context, os, arch)
        requireNotNull(manifestGenerator) {
          "No binary was built for $currentOs and $currentJvmArch"
        }
        requireNotNull(distributionBinary) {
          "No binary was built for $os and $arch"
        }
        val binaryPath = repairUtilityProjectHome(context)?.resolve(manifestGenerator.relativeSourcePath)
        requireNotNull(binaryPath)
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
        val baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
        val artifact = context.paths.artifactDir.resolve("${baseName}${distributionBinary.distributionSuffix}.manifest")
        Files.move(manifest, artifact, StandardCopyOption.REPLACE_EXISTING)
        return@executeStep
      }
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
            val baseUrl = buildContext.applicationInfo.patchesUrl?.removeSuffix("/") ?: error("Missing download url")
            val baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
            val distributionUrls = BINARIES.associate {
              it.distributionUrlVariable to "$baseUrl/$baseName${it.distributionSuffix}"
            }
            distributionUrls.forEach { (envVar, url) ->
              buildContext.messages.info("$envVar=$url")
            }
            runProcess(listOf("bash", "build.sh"), projectHome, buildContext.messages, additionalEnvVariables = distributionUrls)
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
