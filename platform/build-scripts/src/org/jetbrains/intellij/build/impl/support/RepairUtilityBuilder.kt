// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl.support

import com.intellij.openapi.util.SystemInfoRt
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.BuildOptions.Companion.REPAIR_UTILITY_BUNDLE_STEP
import org.jetbrains.intellij.build.JvmArchitecture.Companion.currentJvmArch
import org.jetbrains.intellij.build.OsFamily.Companion.currentOs
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.impl.Docker
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission.*
import java.util.*
import kotlin.time.Duration.Companion.minutes

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
    private val buildLock = Mutex()
    suspend fun bundle(context: BuildContext, os: OsFamily, arch: JvmArchitecture, distributionDir: Path) {
      context.executeStep(spanBuilder("bundle repair-utility").setAttribute("os", os.osName), REPAIR_UTILITY_BUNDLE_STEP) {
        if (!canBinariesBeBuilt(context)) return@executeStep
        val cache = getBinaryCache(context).await()
        val binary = findBinary(os, arch)
        val path = cache.get(binary)
        checkNotNull(path) {
          "No binary was built for $os and $arch"
        }
        val repairUtilityTarget = distributionDir.resolve(binary.relativeTargetPath)
        Span.current().addEvent("copy $path to $repairUtilityTarget")
        withContext(Dispatchers.IO) {
          Files.createDirectories(repairUtilityTarget.parent)
          Files.copy(path, repairUtilityTarget, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }

    suspend fun generateManifest(context: BuildContext, unpackedDistribution: Path, os: OsFamily, arch: JvmArchitecture) {
      context.executeStep(spanBuilder("generate installation integrity manifest")
                            .setAttribute("dir", unpackedDistribution.toString()), REPAIR_UTILITY_BUNDLE_STEP) {
        check(Files.exists(unpackedDistribution)) {
          "$unpackedDistribution doesn't exist"
        }
        if (!canBinariesBeBuilt(context)) return@executeStep
        check(getBinaryCache(context).await().isNotEmpty())
        val manifestGenerator = findBinary(currentOs, currentJvmArch)
        val distributionBinary = findBinary(os, arch)
        val binaryPath = repairUtilityProjectHome(context)?.resolve(manifestGenerator.relativeSourcePath)
        requireNotNull(binaryPath)
        val tmpDir = context.paths.tempDir.resolve(REPAIR_UTILITY_BUNDLE_STEP + UUID.randomUUID().toString())
        withContext(Dispatchers.IO) {
          Files.createDirectories(tmpDir)
          try {
            runProcess(args = listOf(binaryPath.toString(), "hashes", "-g", "--path", unpackedDistribution.toString()),
                       workingDir = tmpDir)
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
          val baseName = baseArtifactName(context)
          val artifact = context.paths.artifactDir.resolve("${baseName}${distributionBinary.distributionSuffix}.manifest")
          Files.move(manifest, artifact, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }

    fun findBinary(os: OsFamily, arch: JvmArchitecture): Binary {
      val binary = BINARIES.find { it.os == os && it.arch == arch }
      checkNotNull(binary) { "Unsupported binary: $os $arch" }
      return binary
    }

    private val binaryCache = WeakHashMap<BuildContext, Deferred<Map<Binary, Path>>>()

    private fun getBinaryCache(context: BuildContext): Deferred<Map<Binary, Path>> {
      synchronized(binaryCache) {
        binaryCache.get(context)?.let {
          return it
        }

        @Suppress("OPT_IN_USAGE")
        val deferred = GlobalScope.async { buildBinaries(context) }
        binaryCache.put(context, deferred)
        return deferred
      }
    }

    private fun canBinariesBeBuilt(context: BuildContext): Boolean {
      return !SystemInfoRt.isWindows &&
             !context.isStepSkipped(REPAIR_UTILITY_BUNDLE_STEP) &&
             repairUtilityProjectHome(context) != null &&
             Docker.isAvailable
    }

    private fun repairUtilityProjectHome(context: BuildContext): Path? {
      val projectHome = context.paths.communityHomeDir.resolve("native/repair-utility")
      if (Files.exists(projectHome)) {
        return projectHome
      }
      else {
        Span.current().addEvent("$projectHome doesn't exist")
        return null
      }
    }

    private fun baseArtifactName(context: BuildContext): String {
      return "${context.applicationInfo.productCode}-${context.buildNumber}"
    }

    private suspend fun buildBinaries(context: BuildContext): Map<Binary, Path> {
      return spanBuilder("build repair-utility").useWithScope {
        val projectHome = repairUtilityProjectHome(context) ?: return@useWithScope emptyMap()
        try {
          val baseUrl = context.productProperties.baseDownloadUrl?.removeSuffix("/") 
                        ?: error("'baseDownloadUrl' is not specified in ${context.productProperties.javaClass.name}")
          val baseName = baseArtifactName(context)
          val distributionUrls = BINARIES.associate {
            it.distributionUrlVariable to "$baseUrl/$baseName${it.distributionSuffix}"
          }
          for ((envVar, url) in distributionUrls) {
            context.messages.info("$envVar=$url")
          }
          buildLock.withLock {
            withContext(Dispatchers.IO) {
              suspendingRetryWithExponentialBackOff {
                runProcess(args = listOf("bash", "build.sh"), workingDir = projectHome,
                           additionalEnvVariables = distributionUrls,
                           timeout = 5.minutes,
                           inheritOut = true)
              }
            }
          }
        }
        catch (e: Throwable) {
          if (TeamCityHelper.isUnderTeamCity) {
            throw e
          }
          return@useWithScope emptyMap<Binary, Path>()
        }

        val binaries = BINARIES.associateWith { projectHome.resolve(it.relativeSourcePath) }
        val executablePermissions = setOf(
          OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE
        )
        withContext(Dispatchers.IO) {
          for (file in binaries.values) {
            if (Files.notExists(file)) {
              context.messages.error("$file doesn't exist")
            }
            Files.setPosixFilePermissions(file, executablePermissions)
          }
        }
        binaries
      }
    }

    private val BINARIES: List<Binary> = listOf(
      Binary(OsFamily.LINUX, JvmArchitecture.x64, "bin/repair-linux-amd64", "bin/repair", "linux_amd64_url"),
      Binary(OsFamily.LINUX, JvmArchitecture.aarch64, "bin/repair-linux-arm64", "bin/repair", "linux_arm64_url"),
      Binary(OsFamily.WINDOWS, JvmArchitecture.x64, "bin/repair.exe", "bin/repair.exe", "windows_amd64_url"),
      Binary(OsFamily.WINDOWS, JvmArchitecture.aarch64, "bin/repair64a.exe", "bin/repair.exe", "windows_arm64_url"),
      Binary(OsFamily.MACOS, JvmArchitecture.x64, "bin/repair-darwin-amd64", "bin/repair", "darwin_amd64_url"),
      Binary(OsFamily.MACOS, JvmArchitecture.aarch64, "bin/repair-darwin-arm64", "bin/repair", "darwin_arm64_url"),
    )

    class Binary(
      @JvmField val os: OsFamily,
      @JvmField val arch: JvmArchitecture,
      @JvmField val relativeSourcePath: String,
      @JvmField val relativeTargetPath: String,
      @JvmField val distributionUrlVariable: String
    ) {
      val distributionSuffix: String
        get() = OsSpecificDistributionBuilder.suffix(arch) + "." + when (os) {
          OsFamily.LINUX -> "tar.gz"
          OsFamily.MACOS -> "dmg"
          OsFamily.WINDOWS -> "exe"
        }
    }

    fun executableFilesPatterns(context: BuildContext): List<String> {
      return if (canBinariesBeBuilt(context)) {
        listOf("bin/repair")
      }
      else emptyList()
    }
  }
}