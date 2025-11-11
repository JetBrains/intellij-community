// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path

open class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 PNG product icon for Linux distribution.
   * If omitted, only an SVG icon will be included.
   */
  var iconPngPath: String? = null

  /**
   * Path to a PNG product icon for EAP builds (if `null`, [iconPngPath] will be used).
   */
  var iconPngPathForEAP: String? = null

  @Suppress("unused")
  @ApiStatus.ScheduledForRemoval
  @Deprecated("New native launcher is always enabled")
  /**
   * Enables the use of the new cross-platform launcher (which loads launch data from `product-info.json` instead of hardcoding into a script).
   * It's now recommended to use the new launcher, so it must always be built. Setting this property to `false` will have no effect.
   */
  var useXPlatLauncher: Boolean = true

  /**
   * Relative paths to files in the Linux distribution which should take 'executable' permissions
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  open fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, targetLibcImpl: LibcImpl, context: BuildContext): Sequence<String> {
    val basePatterns = sequenceOf(
      "bin/*.sh",
      "plugins/**/*.sh",
      "bin/fsnotifier",
      "bin/restarter",
      "bin/${context.productProperties.baseFileName}",
    )

    val rtPatterns = if (includeRuntime) {
      val distribution =
        if (targetLibcImpl == LinuxLibcImpl.MUSL) JetBrainsRuntimeDistribution.LIGHTWEIGHT
        else context.productProperties.runtimeDistribution
      context.bundledRuntime.executableFilesPatterns(OsFamily.LINUX, distribution)
    }
    else {
      emptySequence()
    }

    return basePatterns +
           rtPatterns +
           RepairUtilityBuilder.executableFilesPatterns(context) +
           extraExecutables +
           context.getExtraExecutablePattern(OsFamily.LINUX)
  }

  /**
   * If `true`, a separate `*[org.jetbrains.intellij.build.impl.LinuxDistributionBuilder.NO_RUNTIME_SUFFIX].tar.gz` artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime: Boolean = false

  /**
   * Set both properties if a .snap package should be produced.
   * [snapName] is the name of the package (e.g., "intellij-idea" or "pycharm").
   * [snapDescription] is the plain text description of the package.
   * [snapLegacyAliases] are legacy names for the package, e.g., "intellij-idea-ultimate" or "pycharm-community".
   */
  var snapName: String? = null
  var snapDescription: String? = null
  var snapLegacyAliases: List<String> = emptyList()

  /**
   * Name of the root directory inside the .tar.gz archive.
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "${appInfo.fullProductName}-${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to the Linux distribution of the product.
   */
  open suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
    RepairUtilityBuilder.bundle(context, OsFamily.LINUX, arch, targetDir)
  }
}
