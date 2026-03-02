// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import java.nio.file.Path

/**
 * DSL marker annotation for Linux customizer builder DSL.
 * Prevents implicit receiver leakage in nested scopes.
 */
@DslMarker
annotation class LinuxCustomizerDsl

/**
 * Creates a [LinuxDistributionCustomizer] using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * linuxCustomizer(projectHome) {
 *   iconPngPath = "build/images/linux/icon_128.png"
 *   iconPngPathForEAP = "build/images/linux/icon_EAP_128.png"
 *   snaps += Snap(name = "product-name", description = "Product description")
 *   extraExecutables += "bin/custom-tool"
 *
 *   copyAdditionalFiles { targetDir, arch, context ->
 *     // Custom file copying logic
 *   }
 *
 *   rootDirectoryName { appInfo, buildNumber ->
 *     "product-$buildNumber"
 *   }
 *
 *   executableFilePatterns { base, includeRuntime, arch, targetLibcImpl, context ->
 *     base + listOf("bin/custom/$arch/tool")
 *   }
 * }
 * ```
 */
inline fun linuxCustomizer(projectHome: Path, configure: LinuxCustomizerBuilder.() -> Unit): LinuxDistributionCustomizer {
  return LinuxCustomizerBuilder(projectHome).apply(configure).build()
}

/**
 * Builder class for creating [LinuxDistributionCustomizer] instances using a DSL.
 */
@LinuxCustomizerDsl
class LinuxCustomizerBuilder @PublishedApi internal constructor(private val projectHome: Path) {
  /**
   * Path to 128x128 PNG product icon for Linux distribution, relative to projectHome.
   * Specify as a relative string path (e.g., "build/images/linux/icon.png").
   * Will be automatically resolved against projectHome during build.
   * If omitted, only an SVG icon will be included.
   */
  var iconPngPath: String? = null

  /**
   * Path to PNG product icon for EAP builds, relative to projectHome.
   * If null, [iconPngPath] will be used.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  var iconPngPathForEAP: String? = null

  /**
   * Relative paths to files in the Linux distribution which should take 'executable' permissions.
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  /**
   * If `true`, a separate `*[org.jetbrains.intellij.build.impl.NO_RUNTIME_SUFFIX].tar.gz` artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime: Boolean = false

  /**
   * Add an instance of [Snap] if a .snap package should be produced.
   */
  var snaps: PersistentList<LinuxDistributionCustomizer.Snap> = persistentListOf()

  // Method override handlers (stored as lambdas)
  private var copyAdditionalFilesHandler: (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = null
  private var rootDirectoryNameHandler: ((ApplicationInfoProperties, String) -> String)? = null
  private var executableFilePatternsHandler: ((Sequence<String>, Boolean, JvmArchitecture, LibcImpl, BuildContext) -> Sequence<String>)? = null

  /**
   * Gets the current copyAdditionalFiles handler for wrapping purposes.
   * @return the current handler, or null if none is set
   */
  fun getCopyAdditionalFilesHandler(): (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = copyAdditionalFilesHandler

  /**
   * Adds custom logic for copying additional files to the Linux distribution.
   * This handler is called after the base copyAdditionalFiles logic.
   *
   * @param handler Lambda receiving targetDir, arch, and context
   */
  fun copyAdditionalFiles(handler: suspend (targetDir: Path, arch: JvmArchitecture, context: BuildContext) -> Unit) {
    this.copyAdditionalFilesHandler = handler
  }

  /**
   * Sets a custom root directory name inside the .tar.gz archive.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and buildNumber, returning the root directory name
   */
  fun rootDirectoryName(handler: (ApplicationInfoProperties, String) -> String) {
    this.rootDirectoryNameHandler = handler
  }

  /**
   * Sets custom executable file patterns generator.
   * The handler receives base patterns from the parent customizer, making it easy to extend or filter them.
   *
   * Note: Linux-specific parameter `targetLibcImpl` indicates GLIBC vs MUSL variant.
   *
   * Example:
   * ```kotlin
   * executableFilePatterns { base, _, arch, _, _ ->
   *   base + listOf("bin/custom/$arch/tool")
   * }
   * ```
   *
   * @param handler Lambda receiving base patterns, includeRuntime flag, arch, targetLibcImpl, and context, returning a sequence of patterns
   */
  fun executableFilePatterns(handler: (basePatterns: Sequence<String>, Boolean, JvmArchitecture, LibcImpl, BuildContext) -> Sequence<String>) {
    this.executableFilePatternsHandler = handler
  }

  /**
   * Builds the [LinuxDistributionCustomizer] with the configured settings.
   * Automatically resolves relative paths against projectHome.
   */
  fun build(): LinuxDistributionCustomizer {
    return LinuxDistributionCustomizerImpl(builder = this, projectHome = projectHome)
  }

  private class LinuxDistributionCustomizerImpl(
    private val builder: LinuxCustomizerBuilder,
    private val projectHome: Path,
  ) : LinuxDistributionCustomizer() {
    init {
      builder.iconPngPath?.let { iconPngPath = projectHome.resolve(it) }
      builder.iconPngPathForEAP?.let { iconPngPathForEAP = projectHome.resolve(it) }
      extraExecutables = builder.extraExecutables
      buildArtifactWithoutRuntime = builder.buildArtifactWithoutRuntime
      snaps = builder.snaps
    }

    override suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
      super.copyAdditionalFiles(targetDir = targetDir, arch = arch, context = context)
      builder.copyAdditionalFilesHandler?.invoke(targetDir, arch, context)
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
      return builder.rootDirectoryNameHandler?.invoke(appInfo, buildNumber) ?: super.getRootDirectoryName(appInfo, buildNumber)
    }

    override fun generateExecutableFilesPatterns(
      includeRuntime: Boolean,
      arch: JvmArchitecture,
      targetLibcImpl: LibcImpl,
      context: BuildContext
    ): Sequence<String> {
      val basePatterns = super.generateExecutableFilesPatterns(includeRuntime, arch, targetLibcImpl, context)
      return builder.executableFilePatternsHandler?.invoke(basePatterns, includeRuntime, arch, targetLibcImpl, context)
        ?: basePatterns
    }
  }
}

/**
 * Creates a [LinuxDistributionCustomizer] with Community edition defaults using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * communityLinuxCustomizer(projectHome) {
 *   // Override or extend Community defaults
 *   extraExecutables += "bin/custom-tool"
 * }
 * ```
 */
inline fun communityLinuxCustomizer(projectHome: Path, configure: LinuxCustomizerBuilder.() -> Unit = {}): LinuxDistributionCustomizer {
  return linuxCustomizer(projectHome) {
    // Set Community defaults
    iconPngPath = "build/conf/ideaCE/linux/images/icon_CE_128.png"
    iconPngPathForEAP = "build/conf/ideaCE/linux/images/icon_CE_EAP_128.png"
    snaps += LinuxDistributionCustomizer.Snap(
      name = "intellij-idea",
      description =
        "The most intelligent Java IDE. Every aspect of IntelliJ IDEA is specifically designed to maximize developer productivity. " +
        "Together, powerful static code analysis and ergonomic design make development not only productive but also an enjoyable experience."
    )

    rootDirectoryName { _, buildNumber -> "idea-IC-$buildNumber" }

    executableFilePatterns { base, _, _, _, _ ->
      base.plus(KotlinBinaries.kotlinCompilerExecutables).filterNot { it == "plugins/**/*.sh" }
    }

    // Apply user configuration
    configure()
  }
}

open class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 PNG product icon for Linux distribution.
   * If omitted, only an SVG icon will be included.
   */
  var iconPngPath: Path? = null

  /**
   * Path to a PNG product icon for EAP builds (if `null`, [iconPngPath] will be used).
   */
  var iconPngPathForEAP: Path? = null

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
    val basePatterns = if (context.options.isLanguageServer) sequenceOf("bin/${context.productProperties.baseFileName}")
    else sequenceOf(
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
   * If `true`, a separate `*[org.jetbrains.intellij.build.impl.NO_RUNTIME_SUFFIX].tar.gz` artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime: Boolean = false

  @Deprecated(message = "IJI-2568", replaceWith = ReplaceWith("snaps += Snap(name = value, description = TODO())"))
  var snapName: String? = null

  @Deprecated(message = "IJI-2568", replaceWith = ReplaceWith("snaps += Snap(name = TODO(), description = value)"))
  var snapDescription: String? = null

  /**
   * Add an instance of [Snap] if a .snap package should be produced.
   */
  var snaps: PersistentList<Snap> = persistentListOf()

  /**
   * [name] is the name of the package (e.g., "intellij-idea" or "pycharm").
   * [description] is the plain text description of the package.
   */
  data class Snap(
    val name: String,
    val description: String,
  ) {
    init {
      require(name.isNotBlank()) { "Snap name cannot be blank" }
      require(description.isNotBlank()) { "$name Snap description cannot be blank" }
    }
  }

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
    RepairUtilityBuilder.bundle(OsFamily.LINUX, arch, targetDir, context)
  }
}
