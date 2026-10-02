// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.archiveCacheKey
import org.jetbrains.intellij.build.dependencies.extractToCacheLocation
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.impl.DeclaredResourceGeneratorRun
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.resolveFileForReading
import java.nio.file.Files
import java.nio.file.Path

const val PYREFLY_BUNDLE_ENABLED_PROPERTY: String = "pyrefly.bundle"

private const val PYREFLY_VERSION_PROPERTY: String = "pyreflyBuild"

private const val PYREFLY_GROUP_ID: String = "org.jetbrains.intellij.deps"

private const val PYREFLY_LICENSE_ARTIFACT_ID: String = "pyrefly-license"

private const val PYREFLY_PACKAGING: String = "tar.gz"

private const val PYREFLY_DIR_NAME: String = "pyrefly"

private const val PYREFLY_BINARY_NAME: String = "pyrefly"

/**
 * The license report as the dev distribution copies it: the `license` tree of the unpacked license archive, which the
 * dev-launch extension declares from the same `pyreflyBuild` value.
 */
private val PYREFLY_LICENSE_DEV_SPEC: DevPluginLayoutAssetSpec = DevPluginLayoutAssetSpec(
  sources = listOf(DevPluginLayoutAssetSource.BazelTarget(
    label = "@dev_launch_pyrefly_license_extracted//:files",
    kind = "directory",
    fileName = "license",
    prefix = "license",
  )),
  assets = listOf(DevPluginLayoutAsset(destination = "$PYREFLY_DIR_NAME/license", sources = listOf(0))),
)

/** The binary of one platform as the dev distribution copies it, out of the unpacked platform archive. */
private fun pyreflyBinaryDevSpec(os: OsFamily, arch: JvmArchitecture): DevPluginLayoutAssetSpec {
  val platformDirName = pyreflyPlatformDirName(os, arch)
  val binaryName = os.binaryName(PYREFLY_BINARY_NAME)
  val hostPlatform = "${if (os == OsFamily.MACOS) "darwin" else os.osId}_${arch.name}"
  return DevPluginLayoutAssetSpec(
    sources = listOf(DevPluginLayoutAssetSource.BazelTarget(
      label = "@dev_launch_${hostPlatform}_pyrefly_extracted//:$platformDirName/$binaryName",
      kind = "file",
      fileName = binaryName,
    )),
    assets = listOf(DevPluginLayoutAsset(
      destination = "$PYREFLY_DIR_NAME/$platformDirName/$binaryName",
      sources = listOf(0),
      mode = 493,
    )),
  )
}

/**
 * Declares pyrefly for every distribution. The dev distribution bundles it from these declarations. Production copies
 * the files only when [isPyreflyBundlingEnabled] is true.
 */
fun PluginLayout.PluginLayoutSpec.withBundledPyrefly() {
  withGeneratedResources(PYREFLY_LICENSE_DEV_SPEC, run = DeclaredResourceGeneratorRun.BUNDLED_AND_DEV) { targetDir, context ->
    if (isPyreflyStepEnabled()) {
      copyPyreflyLicenseReport(targetDir, context)
    }
  }

  for (platform in SUPPORTED_DISTRIBUTIONS) {
    val (os, arch) = platform
    withGeneratedPlatformResources(platform, pyreflyBinaryDevSpec(os, arch), run = DeclaredResourceGeneratorRun.BUNDLED_AND_DEV) { targetDir, context ->
      if (isPyreflyStepEnabled()) {
        copyPyreflyBinary(targetDir, context, os, arch)
      }
    }

    if (os != OsFamily.WINDOWS) {
      withPlatformExecutable(platform, "$PYREFLY_DIR_NAME/${pyreflyPlatformDirName(os, arch)}/${os.binaryName(PYREFLY_BINARY_NAME)}")
    }
  }
}

fun PluginLayout.PluginLayoutSpec.withPublishedPyrefly(dist: SupportedDistribution) {
  val (os, arch, _) = dist
  withGeneratedResources(DevPluginLayoutAssetSpec.OMITTED) { targetDir, context ->
    copyPyreflyLicenseReport(targetDir, context)
    copyPyreflyBinary(targetDir, context, os, arch)
  }
}

fun isPyreflyBundlingEnabled(): Boolean = System.getProperty(PYREFLY_BUNDLE_ENABLED_PROPERTY).toBoolean()

private fun isPyreflyStepEnabled(): Boolean {
  if (isPyreflyBundlingEnabled()) {
    return true
  }
  Span.current().addEvent("skip the Pyrefly bundling, because '$PYREFLY_BUNDLE_ENABLED_PROPERTY' is false")
  return false
}

private fun copyPyreflyLicenseReport(targetDir: Path, context: BuildContext) {
  val licenseDir = downloadPyrefly(context, PYREFLY_LICENSE_ARTIFACT_ID).resolve("license")
  check(Files.isDirectory(licenseDir)) {
    "Pyrefly license report is missing from the archive: $licenseDir"
  }
  context.messages.info("Bundling pyrefly license report in $licenseDir")
  copyDir(sourceDir = licenseDir, targetDir = targetDir.resolve(PYREFLY_DIR_NAME).resolve("license"))
}

private fun copyPyreflyBinary(targetDir: Path, context: BuildContext, os: OsFamily, arch: JvmArchitecture) {
  val platformDirName = pyreflyPlatformDirName(os, arch)
  val platformDir = downloadPyrefly(context, pyreflyArtifactId(platformDirName)).resolve(platformDirName)
  val binary = platformDir.resolve(os.binaryName(PYREFLY_BINARY_NAME))
  check(Files.isRegularFile(binary)) {
    "Pyrefly binary for ${os.osName} ${arch.archName} is missing from the archive: $binary"
  }
  context.messages.info("Bundling pyrefly binary at $binary into ${os.osName} ${arch.archName}")
  copyFileToDir(binary, targetDir.resolve(PYREFLY_DIR_NAME).resolve(platformDirName))
}

private fun downloadPyrefly(context: BuildContext, artifactId: String): Path {
  val communityRoot = context.paths.communityHomeDirRoot
  val version = context.dependenciesProperties.property(PYREFLY_VERSION_PROPERTY)
  val uri = BuildDependenciesDownloader.getUriForMavenArtifact(INTELLIJ_DEPENDENCIES_URL, PYREFLY_GROUP_ID, artifactId, version, PYREFLY_PACKAGING)
  val resolved = resolveFileForReading(uri.toString(), communityRoot, context.httpSession)
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
