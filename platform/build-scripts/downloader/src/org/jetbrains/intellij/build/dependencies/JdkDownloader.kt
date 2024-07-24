// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Provides a current JBR SDK
 */
object JdkDownloader {
  fun blockingGetJdkHome(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null, infoLog: (String) -> Unit): Path {
    return runBlocking(Dispatchers.IO) {
      getJdkHome(communityRoot, jdkBuildNumber, variation, infoLog)
    }
  }

  suspend fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null, infoLog: (String) -> Unit): Path {
    val os = OS.current
    val arch = Arch.current
    return getJdkHome(communityRoot = communityRoot, os = os, arch = arch, infoLog = infoLog, jdkBuildNumber = jdkBuildNumber, variation = variation)
  }

  @JvmStatic
  fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot, jdkBuildNumber: String? = null, variation: String? = null): Path {
    return blockingGetJdkHome(communityRoot, jdkBuildNumber, variation) {
      Logger.getLogger(JdkDownloader::class.java.name).info(it)
    }
  }

  /**
   * Will be used by JpsBootstrapMain when IJI-2074 is ready
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
    jdkBuildNumber: String? = null,
    variation: String? = null,
    infoLog: (String) -> Unit,
  ): Path {
    val jdkUrl = getUrl(communityRoot = communityRoot, os = os, arch = arch, jdkBuildNumber = jdkBuildNumber, variation = variation)
    val jdkArchive = downloadFileToCacheLocation(url = jdkUrl.toString(), communityRoot = communityRoot)
    val jdkExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot = communityRoot,
                                                                              archiveFile = jdkArchive,
                                                                              BuildDependenciesExtractOptions.STRIP_ROOT)
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

  private fun getUrl(communityRoot: BuildDependenciesCommunityRoot, os: OS, arch: Arch, jdkBuildNumber: String? = null, variation: String? = null): URI {
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
    return URI.create("https://cache-redirector.jetbrains.com/intellij-jbr/" +
                      (variation ?: "jbrsdk") + "-" +
                      version + "-" + osString + "-" +
                      archString + "-" + build + ext)
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
}
