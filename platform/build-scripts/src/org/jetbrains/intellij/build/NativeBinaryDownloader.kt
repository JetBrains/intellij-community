// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

object NativeBinaryDownloader {
  private const val GROUP_ID = "org.jetbrains.intellij.deps"
  private const val RESTARTER_ID = "restarter"
  private const val PACKAGING = "tar.gz"

  /**
   * Downloads and unpacks the restarter tarball and returns a path to an executable for the given platform.
   */
  suspend fun downloadRestarter(context: BuildContext, os: OsFamily, arch: JvmArchitecture): Path {
    val communityRoot = context.paths.communityHomeDirRoot

    val version = context.dependenciesProperties.property("restarterBuild")
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(INTELLIJ_DEPENDENCIES_URL, GROUP_ID, RESTARTER_ID, version, PACKAGING)
    val archiveFile = downloadFileToCacheLocation(uri.toString(), communityRoot)
    val unpackedDir = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, archiveFile)

    val platformDir = unpackedDir.resolve("${os.osName}-${arch.archName}")
    check(platformDir.isDirectory()) { "'${platformDir.fileName}' not found in '${archiveFile.fileName}'" }

    val executableFile = platformDir.resolve(if (os == OsFamily.WINDOWS) "restarter.exe" else "restarter")
    check(executableFile.isRegularFile()) { "Executable '${executableFile.fileName}' not found in '${platformDir}'" }

    return executableFile
  }
}
