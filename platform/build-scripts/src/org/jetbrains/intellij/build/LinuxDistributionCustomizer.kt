// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path

abstract class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 PNG product icon for Linux distribution.
   * If omitted, only an SVG icon will be included.
   */
  var iconPngPath: String? = null

  /**
   * Path to a PNG product icon for EAP builds (if `null`, [iconPngPath] will be used).
   */
  var iconPngPathForEAP: String? = null

  /**
   * Relative paths to files in Linux distribution which should take 'executable' permissions
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  open fun generateExecutableFilesPatterns(context: BuildContext, includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
    val basePatterns = persistentListOf(
      "bin/*.sh",
      "plugins/**/*.sh",
      "bin/fsnotifier",
      "bin/restarter"
    )

    val rtPatterns =
      if (includeRuntime) context.bundledRuntime.executableFilesPatterns(OsFamily.LINUX, context.productProperties.runtimeDistribution)
      else emptyList()

    return basePatterns +
           rtPatterns +
           RepairUtilityBuilder.executableFilesPatterns(context) +
           extraExecutables +
           context.getExtraExecutablePattern(OsFamily.LINUX)
  }

  /**
   * If `true`, a separate *[org.jetbrains.intellij.build.impl.LinuxDistributionBuilder.NO_RUNTIME_SUFFIX].tar.gz artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime = false

  /**
   * Set both properties if a .snap package should be produced.
   * "snapName" is the name of the package (e.g., "intellij-idea-ultimate" or "pycharm-community").
   * "snapDescription" is the plain text description of the package.
   */
  var snapName: String? = null
  var snapDescription: String? = null

  /**
   * Name of the root directory inside the .tar.gz archive.
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "${appInfo.productName}-${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"

  /**
   * Override this method to copy additional files to the Linux distribution of the product.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    RepairUtilityBuilder.bundle(context = context, os = OsFamily.LINUX, arch = arch, distributionDir = targetDir)

    @Suppress("DEPRECATION")
    copyAdditionalFilesBlocking(context, targetDir, arch)
  }

  @Deprecated("Please migrate the build script to Kotlin and override `copyAdditionalFiles`")
  open fun copyAdditionalFilesBlocking(context: BuildContext, targetDir: Path, arch: JvmArchitecture) { }
}
