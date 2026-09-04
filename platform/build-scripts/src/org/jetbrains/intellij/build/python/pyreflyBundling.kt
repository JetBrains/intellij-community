// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.archiveCacheKey
import org.jetbrains.intellij.build.dependencies.extractToCacheLocation
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.resolveFileForReading
import java.nio.file.Files
import java.nio.file.Path

private const val PYREFLY_BUNDLE_ENABLED_PROPERTY: String = "pyrefly.bundle"

private const val PYREFLY_VERSION_PROPERTY: String = "pyreflyBuild"

private const val PYREFLY_GROUP_ID: String = "org.jetbrains.intellij.deps"

private const val PYREFLY_LICENSE_ARTIFACT_ID: String = "pyrefly-license"

private const val PYREFLY_PACKAGING: String = "tar.gz"

private const val PYREFLY_DIR_NAME: String = "pyrefly"

private const val PYREFLY_BINARY_NAME: String = "pyrefly"

internal fun PluginLayout.PluginLayoutSpec.withBundledPyrefly() {
  if (!isPyreflyBundlingEnabled()) return

  withGeneratedResources { targetDir, context -> copyPyreflyLicenseReport(targetDir, context) }

  for ((os, arch, libc) in SUPPORTED_DISTRIBUTIONS) {
    withGeneratedPlatformResources(os, arch, libc) { targetDir, context -> copyPyreflyBinary(targetDir, context, os, arch) }

    if (os != OsFamily.WINDOWS) {
      withPlatformExecutable(os, arch, libc, "$PYREFLY_DIR_NAME/${os.binaryName(PYREFLY_BINARY_NAME)}")
    }
  }
}

private fun isPyreflyBundlingEnabled(): Boolean = System.getProperty(PYREFLY_BUNDLE_ENABLED_PROPERTY).toBoolean()

private suspend fun copyPyreflyLicenseReport(targetDir: Path, context: BuildContext) {
  val licenseDir = downloadPyrefly(context, PYREFLY_LICENSE_ARTIFACT_ID).resolve("license")
  check(Files.isDirectory(licenseDir)) {
    "Pyrefly license report is missing from the archive: $licenseDir"
  }
  context.messages.info("Bundling pyrefly license report in $licenseDir")
  copyDir(sourceDir = licenseDir, targetDir = targetDir.resolve(PYREFLY_DIR_NAME).resolve("license"))
}

private suspend fun copyPyreflyBinary(targetDir: Path, context: BuildContext, os: OsFamily, arch: JvmArchitecture) {
  val platformDirName = pyreflyPlatformDirName(os, arch)
  val platformDir = downloadPyrefly(context, pyreflyArtifactId(platformDirName)).resolve(platformDirName)
  val binary = platformDir.resolve(os.binaryName(PYREFLY_BINARY_NAME))
  check(Files.isRegularFile(binary)) {
    "Pyrefly binary is missing from the archive: $binary"
  }
  context.messages.info("Bundling pyrefly binary at $binary into ${os.osName} ${arch.archName}")
  copyFileToDir(binary, targetDir.resolve(PYREFLY_DIR_NAME))
}

private suspend fun downloadPyrefly(context: BuildContext, artifactId: String): Path {
  val communityRoot = context.paths.communityHomeDirRoot
  val version = context.dependenciesProperties.property(PYREFLY_VERSION_PROPERTY)
  val uri = BuildDependenciesDownloader.getUriForMavenArtifact(INTELLIJ_DEPENDENCIES_URL, PYREFLY_GROUP_ID, artifactId, version, PYREFLY_PACKAGING)
  val resolved = resolveFileForReading(uri.toString(), communityRoot)
  return extractToCacheLocation(
    archiveFile = resolved.file,
    communityRoot = communityRoot,
    cacheKey = archiveCacheKey(archiveFile = resolved.file, sha256 = resolved.sha256),
    options = emptyArray(),
  )
}

private fun pyreflyPlatformDirName(os: OsFamily, arch: JvmArchitecture): String = "${os.osName}-${arch.archName}"

/** Matches `PyreflyArtifactBuild.mavenArtifactId` in the TeamCity configuration. */
private fun pyreflyArtifactId(platformDirName: String): String = "pyrefly-${platformDirName.lowercase()}"
