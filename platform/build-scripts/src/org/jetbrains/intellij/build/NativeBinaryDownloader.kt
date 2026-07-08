// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.TerminalLibGhosttyVtDownloader
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText

object NativeBinaryDownloader {
  private const val GROUP_ID = "org.jetbrains.intellij.deps"
  private const val LAUNCHER_ID = "launcher"
  private const val RESTARTER_ID = "restarter"
  private const val LIBWEBP_ID = "libwebp"
  private const val PACKAGING = "tar.gz"
  private const val LICENSE_FILE_NAME = "xplat-launcher-third-party-licenses.html"

  /**
   * Attempts to locate a local debug build of the launcher when in the development mode
   * and [org.jetbrains.intellij.build.BuildOptions.useLocalLauncher] is set to `true`.
   *
   * Otherwise, downloads and unpacks the launcher tarball.
   *
   * Returns a tuple of paths `(executable, license, extra-file?)` for the given platform.
   * The `extra-file` is specific to the platform – e.g., a Windows console executable.
   */
  suspend fun getLauncher(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Triple<Path, Path, Path?> {
    if (context.options.isInDevelopmentMode && context.options.useLocalLauncher) {
      val localLauncher = findLocalLauncher(context, os)
      if (localLauncher != null) return localLauncher
    }

    val (archiveFile, unpackedDir) = downloadAndUnpack(context, "launcherBuild", LAUNCHER_ID)
    val executableFile = findFile(archiveFile, unpackedDir, binName(os, arch, "xplat-launcher"))
    val licenseFile = findFile(archiveFile, unpackedDir, "license/${LICENSE_FILE_NAME}")
    val extraFile = when (os) {
      OsFamily.WINDOWS -> unpackedDir.resolve(binName(os, arch, "xplat-launcher-win-con"))
      else -> null
    }?.takeIf { it.isRegularFile() }
    return Triple(executableFile, licenseFile, extraFile)
  }

  private fun findLocalLauncher(context: BuildContext, os: OsFamily): Triple<Path, Path, Path?>? {
    val targetDir = context.paths.communityHomeDirRoot.communityRoot.resolve("native/XPlatLauncher/target/debug")
    if (targetDir.isDirectory()) {
      val executableFile = targetDir.resolve(os.binaryName("xplat-launcher"))
      if (executableFile.isRegularFile()) {
        val licenseFile = targetDir.resolve(LICENSE_FILE_NAME)
        if (!licenseFile.exists()) {
          licenseFile.writeText("(cross-platform launcher license file stub)", options = arrayOf(StandardOpenOption.CREATE_NEW))
        }
        val extraFile = targetDir.resolve(os.binaryName("xplat-launcher-win-con")).takeIf { it.isRegularFile() }
        return Triple(executableFile, licenseFile, extraFile)
      }
    }

    return null
  }

  /**
   * Downloads and unpacks the restart helper tarball and returns a path to an executable for the given platform.
   */
  suspend fun getRestarter(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path {
    val (archiveFile, unpackedDir) = downloadAndUnpack(context, "restarterBuild", RESTARTER_ID)
    return findFile(archiveFile, unpackedDir, binName(os, arch, "restarter"))
  }

  /**
   * Downloads and unpacks the WebP tarball and returns a path to a library for the given platform.
   */
  suspend fun getLibWebp(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path {
    val (archiveFile, unpackedDir) = downloadAndUnpack(context, "libwebpVersion", LIBWEBP_ID)
    return findFile(archiveFile, unpackedDir, libName(os, arch, "webp_jni"))
  }

  /**
   * Downloads and unpacks the libghostty-vt archive and returns a path to a library for the given platform.
   */
  fun getLibGhosttyVt(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path {
    val unpackedDir = TerminalLibGhosttyVtDownloader.getOrDownloadLibRoot(context.paths.communityHomeDirRoot)
    // match `LibGhosttyVtLocator.libraryPath` with lowercase directory names
    val relativePath = "${os.osName.lowercase()}-${arch.archName.lowercase()}/${os.libraryName("ghostty-vt")}"
    val file = unpackedDir.resolve(relativePath)
    check(file.isRegularFile()) {
      "Library '${relativePath}' not found in '${unpackedDir}'"
    }
    return file
  }

  private suspend fun downloadAndUnpack(context: BuildContext, propertyName: String, artifactId: String): Pair<Path, Path> {
    val communityRoot = context.paths.communityHomeDirRoot
    val version = context.dependenciesProperties.property(propertyName)
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(INTELLIJ_DEPENDENCIES_URL, GROUP_ID, artifactId, version, PACKAGING)
    val archiveFile = downloadFileToCacheLocation(uri.toString(), communityRoot)
    val unpackedDir = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, archiveFile)
    return archiveFile to unpackedDir
  }

  private fun binName(os: OsFamily, arch: JvmArchitecture, baseName: String): String = "${os.osName}-${arch.archName}/${os.binaryName(baseName)}"

  @Suppress("SameParameterValue")
  private fun libName(os: OsFamily, arch: JvmArchitecture, baseName: String): String = "${os.osName}-${arch.archName}/${os.libraryName(baseName)}"

  private fun findFile(archiveFile: Path, unpackedDir: Path, relativePath: String): Path {
    val file = unpackedDir.resolve(relativePath)
    check(file.isRegularFile()) { "Executable '${relativePath}' not found in '${archiveFile.fileName}'" }
    return file
  }
}
