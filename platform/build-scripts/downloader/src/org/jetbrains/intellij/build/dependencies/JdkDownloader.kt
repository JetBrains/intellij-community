// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Provides a current JBR SDK
 */
object JdkDownloader {
  @Deprecated("Use getJdkHome(communityRoot, jdkBuildNumber, variation, infoLog)", level = DeprecationLevel.WARNING)
  fun blockingGetJdkHome(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null, infoLog: (String) -> Unit): Path {
    return runBlocking(Dispatchers.IO) {
      getJdkHome(communityRoot = communityRoot, jdkBuildNumber = jdkBuildNumber, variation = variation, infoLog = infoLog)
    }
  }

  suspend fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null, infoLog: (String) -> Unit): Path {
    val isMusl = isLinuxMusl()
    return getJdkHome(
      communityRoot = communityRoot,
      os = OS.current,
      arch = Arch.current,
      isMusl = isMusl,
      infoLog = infoLog,
      jdkBuildNumber = jdkBuildNumber,
      variation = if (isMusl) null else variation
    )
  }

  suspend fun getJdkHomeAndLog(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null): Path {
    return getJdkHome(communityRoot = communityRoot, jdkBuildNumber = jdkBuildNumber, variation = variation, infoLog = {
      Logger.getLogger(JdkDownloader::class.java.name).info(it)
    })
  }

  fun blockingGetJdkHomeAndLog(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null): Path {
    return runBlocking(Dispatchers.IO) {
      getJdkHomeAndLog(communityRoot, jdkBuildNumber, variation)
    }
  }

  /**
   * Used by JpsBootstrapMain
   */
  @Suppress("unused")
  @JvmStatic
  fun getRuntimeHome(communityRoot: BuildDependenciesCommunityRoot): Path {
    return runBlocking(Dispatchers.IO) {
      val dependenciesProperties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
      val runtimeBuild = dependenciesProperties.property("runtimeBuild")
      getJdkHome(communityRoot, jdkBuildNumber = runtimeBuild, variation = "jbr_jcef") {
        Logger.getLogger(JdkDownloader::class.java.name).info(it)
      }
    }
  }

  suspend fun getJdkHome(
    communityRoot: BuildDependenciesCommunityRoot,
    os: OS,
    arch: Arch,
    isMusl: Boolean = false,
    jdkBuildNumber: String? = null,
    variation: String? = null,
    infoLog: (String) -> Unit,
  ): Path {
    val effectiveVariation = if (isMusl) null else variation
    val jdkUrl = getUrl(communityRoot = communityRoot, os = os, arch = arch, isMusl = isMusl, jdkBuildNumber = jdkBuildNumber, variation = effectiveVariation)
    val jdkArchive = downloadFileToCacheLocation(url = jdkUrl, communityRoot = communityRoot)
    val jdkExtracted = extractFileToCacheLocation(communityRoot = communityRoot, archiveFile = jdkArchive, stripRoot = true)
    val jdkHome = if (os == OS.MACOSX) jdkExtracted.resolve("Contents").resolve("Home") else jdkExtracted
    infoLog("JPS-bootstrap JDK (jdkHome=$jdkHome, executable=${getJavaExecutable(jdkHome)})")
    return jdkHome
  }

  @JvmStatic
  fun getJavaExecutable(jdkHome: Path): Path {
    for (candidateRelative in mutableListOf("bin/java", "bin/java.exe")) {
      val candidate = jdkHome.resolve(candidateRelative)
      if (Files.exists(candidate)) {
        return candidate
      }
    }
    throw IllegalStateException("No java executables were found under $jdkHome")
  }

  private fun getUrl(communityRoot: BuildDependenciesCommunityRoot, os: OS, arch: Arch, isMusl: Boolean = false, jdkBuildNumber: String? = null, variation: String? = null): String {
    val ext = ".tar.gz"
    val osString: String = when (os) {
      OS.WINDOWS -> "windows"
      OS.MACOSX -> "osx"
      OS.LINUX -> "linux"
    }
    val archString: String = when (arch) {
      Arch.X86_64 -> "x64"
      Arch.ARM64 -> "aarch64"
    }

    val jdkBuild = if (jdkBuildNumber == null) {
      val dependencyProperties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
      dependencyProperties.property("jdkBuild")
    } else {
      jdkBuildNumber
    }
    val jdkBuildSplit = jdkBuild.split("b".toRegex()).dropLastWhile { it.isEmpty() }
    check(jdkBuildSplit.size == 2) { "Malformed jdkBuild property: $jdkBuild" }
    val version = jdkBuildSplit[0]
    val build = "b" + jdkBuildSplit[1]
    return "https://cache-redirector.jetbrains.com/intellij-jbr/" +
                      (variation ?: "jbrsdk") + "-" +
                      version + "-" + osString + "-" +
                      (if (isMusl) "musl-" else "") +
                      archString + "-" + build + ext
  }

  enum class OS {
    WINDOWS,
    MACOSX,
    LINUX;

    companion object {
      val current: OS
        get() {
          val osName = System.getProperty("os.name").lowercase()
          return when {
            osName.startsWith("mac") -> MACOSX
            osName.startsWith("linux") -> LINUX
            osName.startsWith("windows") -> WINDOWS
            else -> throw IllegalStateException("Only Mac/Linux/Windows are supported now, current os: $osName")
          }
        }
    }
  }

  enum class Arch {
    X86_64,
    ARM64;

    companion object {
      val current: Arch
        get() {
          val arch = System.getProperty("os.arch").lowercase()
          if ("x86_64" == arch || "amd64" == arch) return X86_64
          if ("aarch64" == arch || "arm64" == arch) return ARM64
          throw IllegalStateException("Only X86_64 and ARM64 are supported, current arch: $arch")
        }
    }
  }

  @ApiStatus.Internal
  fun isLinuxMusl() : Boolean = isRunOnLinuxMusl

  private val isRunOnLinuxMusl: Boolean by lazy {
    if (OS.current != OS.LINUX) {
      false
    }
    else {
      runCatching {
        val process = ProcessBuilder()
          .command("ldd", "--version")
          .redirectErrorStream(true)
          .start()

        val output = process.inputStream.bufferedReader().use { it.readText().lowercase() }

        val lddOutputContainsMusl = if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          false
        }
        else {
          // Check for musl in output
          output.contains("musl")
        }
        Logger.getLogger(JdkDownloader::class.java.name).info("Linux 'ldd --version': ${output}")
        lddOutputContainsMusl
      }.getOrElse {
        Logger.getLogger(JdkDownloader::class.java.name).info("Failed to detect musl libc: ${it.message}")
        false
      }
    }
  }
}
