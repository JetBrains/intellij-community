// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Provides a reasonable stable version of JDK for current platform
 *
 * JDK is used for compiling and running build scripts, compiling intellij project
 * It's currently fixed here to be the same on all build agents and also in Docker images
 */
object JdkDownloader {
  @JvmStatic
  fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot, infoLog: (String) -> Unit): Path {
    val os = OS.current
    val arch = Arch.current
    return getJdkHome(communityRoot, os, arch, infoLog)
  }

  @JvmStatic
  fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot): Path {
    return getJdkHome(communityRoot) { msg: String? ->
      Logger.getLogger(JdkDownloader::class.java.name).info(msg)
    }
  }

  fun getJdkHome(communityRoot: BuildDependenciesCommunityRoot, os: OS, arch: Arch, infoLog: (String) -> Unit): Path {
    val jdkUrl = getUrl(communityRoot, os, arch)
    val jdkArchive = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, jdkUrl)
    val jdkExtracted = BuildDependenciesDownloader.extractFileToCacheLocation(
      communityRoot, jdkArchive, BuildDependenciesExtractOptions.STRIP_ROOT)
    infoLog("jps-bootstrap JDK is at $jdkExtracted")

    val jdkHome: Path = if (os == OS.MACOSX) {
      jdkExtracted.resolve("Contents").resolve("Home")
    }
    else {
      jdkExtracted
    }
    val executable = getJavaExecutable(jdkHome)
    infoLog("JDK home is at $jdkHome, executable at $executable")
    return jdkHome
  }

  fun getJavaExecutable(jdkHome: Path): Path {
    for (candidateRelative in mutableListOf("bin/java", "bin/java.exe")) {
      val candidate = jdkHome.resolve(candidateRelative)
      if (Files.exists(candidate)) {
        return candidate
      }
    }
    throw IllegalStateException("No java executables were found under $jdkHome")
  }

  private fun getUrl(communityRoot: BuildDependenciesCommunityRoot, os: OS, arch: Arch): URI {
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

    val dependenciesProperties = BuildDependenciesDownloader.getDependenciesProperties(communityRoot)
    val jdkBuild = dependenciesProperties.property("jdkBuild")
    val jdkBuildSplit = jdkBuild.split("b".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    check(jdkBuildSplit.size == 2) { "Malformed jdkBuild property: $jdkBuild" }
    val version = jdkBuildSplit[0]
    val build = "b" + jdkBuildSplit[1]
    return URI.create("https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-" +
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
