// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
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
  private const val PACKAGING = "tar.gz"
  private const val LICENSE_FILE_NAME = "xplat-launcher-third-party-licenses.html"

  /**
   * Attempts to locate a local debug build of cross-platform launcher when in the development mode
   * and [org.jetbrains.intellij.build.BuildOptions.useLocalLauncher] is set to `true`.
   *
   * Otherwise, Downloads and unpacks the launcher tarball.
   *
   * Returns a pair of paths `(executable, license)` for the given platform.
   */
  suspend fun getLauncher(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Pair<Path, Path> {
    if (context.options.isInDevelopmentMode && context.options.useLocalLauncher) {
      val localLauncher = findLocalLauncher(context, os)
      if (localLauncher != null) return localLauncher
    }

    val (archiveFile, unpackedDir) = downloadAndUnpack(context, "launcherBuild", LAUNCHER_ID)
    val executableFile = findExecutable(archiveFile, unpackedDir, os, arch, "xplat-launcher")
    val licenseFile = findFile(archiveFile, unpackedDir, "license/${LICENSE_FILE_NAME}")
    return executableFile to licenseFile
  }

  private fun findLocalLauncher(context: BuildContext, os: OsFamily): Pair<Path, Path>? {
    val targetDir = context.paths.communityHomeDirRoot.communityRoot.resolve("native/XPlatLauncher/target/debug")
    if (targetDir.isDirectory()) {
      val executableName = "xplat-launcher${if (os == OsFamily.WINDOWS) ".exe" else ""}"
      val executableFile = targetDir.resolve(executableName)
      if (executableFile.isRegularFile()) {
        val licenseFile = targetDir.resolve(LICENSE_FILE_NAME)
        if (!licenseFile.exists()) {
          licenseFile.writeText("(cross-platform launcher license file stub)", options = arrayOf(StandardOpenOption.CREATE_NEW))
        }
        return executableFile to licenseFile
      }
    }

    return null
  }

  /**
   * Downloads and unpacks the restarter tarball and returns a path to an executable for the given platform.
   */
  suspend fun getRestarter(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path {
    val (archiveFile, unpackedDir) = downloadAndUnpack(context, "restarterBuild", RESTARTER_ID)
    return findExecutable(archiveFile, unpackedDir, os, arch, "restarter")
  }

  private suspend fun downloadAndUnpack(context: BuildContext, propertyName: String, artifactId: String): Pair<Path, Path> {
    val communityRoot = context.paths.communityHomeDirRoot
    val version = context.dependenciesProperties.property(propertyName)
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(INTELLIJ_DEPENDENCIES_URL, GROUP_ID, artifactId, version, PACKAGING)
    val archiveFile = downloadFileToCacheLocation(uri.toString(), communityRoot)
    val unpackedDir = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, archiveFile)
    return archiveFile to unpackedDir
  }

  private fun findExecutable(archiveFile: Path, unpackedDir: Path, os: OsFamily, arch: JvmArchitecture, baseName: String): Path =
    findFile(archiveFile, unpackedDir, "${os.osName}-${arch.archName}/${baseName}${if (os == OsFamily.WINDOWS) ".exe" else ""}")

  private fun findFile(archiveFile: Path, unpackedDir: Path, relativePath: String): Path {
    val file = unpackedDir.resolve(relativePath)
    check(file.isRegularFile()) { "Executable '${relativePath}' not found in '${archiveFile.fileName}'" }
    return file
  }
}
