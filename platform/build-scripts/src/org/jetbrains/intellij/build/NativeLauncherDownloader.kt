// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL as REPOSITORY_URL

object NativeLauncherDownloader {
  /**
   * Downloads and unpacks the launcher tarball and returns a pair of paths (executable, license) for the given platform.
   */
  suspend fun downloadLauncher(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Pair<Path, Path> {
    val communityRoot = context.paths.communityHomeDirRoot

    val version = context.dependenciesProperties.property("launcherBuild")
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(REPOSITORY_URL, GROUP_ID, ARTIFACT_ID, version, PACKAGING)
    val archiveFile = downloadFileToCacheLocation(uri.toString(), communityRoot)
    val unpackedDir = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, archiveFile)

    val platformDirName = PLATFORMS[os to arch] ?: throw IllegalArgumentException("Unknown platform: ${os} / ${arch}")
    val platformDir = unpackedDir.resolve(platformDirName)
    check(Files.isDirectory(platformDir)) { "'${platformDir}' not found in '${archiveFile.fileName}'" }

    val executableName = executableName(os)
    val executableFile = platformDir.resolve(executableName)
    check(Files.isRegularFile(executableFile)) { "Executable '${executableName}' not found in '${platformDir}'" }

    val licenseFile = platformDir.resolve(LICENSE_FILE_NAME)
    check(Files.isRegularFile(licenseFile)) { "Third-party licenses file ${LICENSE_FILE_NAME} not found in '${platformDir}'" }

    return executableFile to licenseFile
  }

  /**
   * Attempts to locate a local debug build of cross-platform launcher.
   * Not available outside the development mode.
   */
  fun findLocalLauncher(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Pair<Path, Path>? {
    check(os to arch in PLATFORMS) { "Unknown platform: ${os} / ${arch}" }

    if (context.options.isInDevelopmentMode) {
      val targetDir = context.paths.communityHomeDirRoot.communityRoot.resolve("native/XPlatLauncher/target/debug")
      if (Files.isDirectory(targetDir)) {
        val executableName = executableName(os)
        val executableFile = targetDir.resolve(executableName)
        if (Files.isRegularFile(executableFile)) {
          val licenseFile = targetDir.resolve(LICENSE_FILE_NAME)
          if (!Files.exists(licenseFile)) {
            Files.writeString(licenseFile, "(cross-platform launcher license file stub)", StandardOpenOption.CREATE_NEW)
          }
          return executableFile to licenseFile
        }
      }
    }

    return null
  }

  private const val GROUP_ID = "org.jetbrains.intellij.deps"
  private const val ARTIFACT_ID = "launcher"
  private const val PACKAGING = "tar.gz"

  private val PLATFORMS = mapOf(
    (OsFamily.WINDOWS to JvmArchitecture.x64) to "x86_64-pc-windows-msvc",
    (OsFamily.WINDOWS to JvmArchitecture.aarch64) to "aarch64-pc-windows-msvc",
    (OsFamily.MACOS to JvmArchitecture.x64) to "x86_64-apple-darwin",
    (OsFamily.MACOS to JvmArchitecture.aarch64) to "aarch64-apple-darwin",
    (OsFamily.LINUX to JvmArchitecture.x64) to "x86_64-unknown-gnu-linux",
    (OsFamily.LINUX to JvmArchitecture.aarch64) to "aarch64-unknown-gnu-linux"
  )

  private fun executableName(osFamily: OsFamily) = when(osFamily) {
    OsFamily.WINDOWS -> "xplat-launcher.exe"
    OsFamily.MACOS, OsFamily.LINUX -> "xplat-launcher"
  }

  private const val LICENSE_FILE_NAME = "xplat-launcher-third-party-licenses.html"
}
