// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path
import java.util.UUID
import java.util.function.Predicate

/**
 * DSL marker annotation for Mac customizer builder DSL.
 * Prevents implicit receiver leakage in nested scopes.
 */
@DslMarker
annotation class MacCustomizerDsl

/**
 * Creates a [MacDistributionCustomizer] using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * macCustomizer(projectHome) {
 *   icnsPath = "build/images/mac/idea.icns"
 *   icnsPathForEAP = "build/images/mac/idea_EAP.icns"
 *   bundleIdentifier = "com.jetbrains.intellij"
 *   dmgImagePath = "build/images/mac/dmg_background.tiff"
 *   fileAssociations = FileAssociation.from("java", "kt", "gradle")
 *   urlSchemes = listOf("idea")
 *   associateIpr = true
 *
 *   copyAdditionalFiles { context, targetDir, arch ->
 *     // Custom file copying logic
 *   }
 *
 *   customIdeaProperties { appInfo ->
 *     mapOf("custom.property" to "value")
 *   }
 *
 *   executableFilePatterns { base, _, arch, _ ->
 *     base + listOf("bin/custom/$arch/tool")
 *   }
 * }
 * ```
 */
inline fun macCustomizer(projectHome: Path, configure: MacCustomizerBuilder.() -> Unit): MacDistributionCustomizer {
  return MacCustomizerBuilder(projectHome).apply(configure).build()
}

/**
 * Builder class for creating [MacDistributionCustomizer] instances using a DSL.
 */
@MacCustomizerDsl
class MacCustomizerBuilder @PublishedApi internal constructor(private val projectHome: Path) {
  /**
   * Path to .icns file containing product bundle icons, relative to projectHome.
   * Specify as a relative string path (e.g., "build/resources/icon.icns").
   * Will be automatically resolved against projectHome during build.
   */
  var icnsPath: String? = null

  /**
   * Path to .icns file for EAP builds, relative to projectHome.
   * If null, [icnsPath] will be used.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  var icnsPathForEAP: String? = null

  /**
   * Path to alternative .icns file in macOS Big Sur style, relative to projectHome.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  @Deprecated("BigSur-style icons are now used by default")
  var icnsPathForAlternativeIcon: String? = null

  /**
   * Path to alternative .icns file in macOS Big Sur style for EAP, relative to projectHome.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  @Deprecated("BigSur-style icons are now used by default")
  var icnsPathForAlternativeIconForEAP: String? = null

  /**
   * A unique identifier string that specifies the app type of the bundle (CFBundleIdentifier).
   * The string should be in reverse DNS format using only the Roman alphabet in upper and lower case (A-Z, a-z), dots ('.'), and hyphens ('-').
   */
  var bundleIdentifier: String? = null

  /**
   * Path to image which will be injected into the .dmg file, relative to projectHome.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  var dmgImagePath: String? = null

  /**
   * Path to image which will be injected into .dmg file for EAP builds, relative to projectHome.
   * If null, [dmgImagePath] will be used.
   * Specify as a relative string path - will be resolved against projectHome during build.
   */
  var dmgImagePathForEAP: String? = null

  /**
   * The minimum version of macOS where the product is allowed to be installed.
   */
  var minOSXVersion: String = "10.13"

  /**
   * String with declarations of additional file types that should be automatically opened by the application.
   */
  var additionalDocTypes: String = ""

  /**
   * List of file associations. Note that users won't be able to switch off some of these associations during installation.
   */
  var fileAssociations: List<FileAssociation> = emptyList()

  /**
   * List of URL schemes (e.g., "idea" for "idea://open?file=/some/file").
   */
  var urlSchemes: List<String> = emptyList()

  /**
   * If true, *.ipr files will be associated with the product in Info.plist.
   */
  var associateIpr: Boolean = false

  /**
   * Filter for files that is going to be put to <distribution>/bin directory.
   */
  var binFilesFilter: Predicate<Path> = Predicate { true }

  /**
   * If true, a separate artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime: Boolean = System.getProperty(MacDistributionCustomizer.BUILD_ARTIFACT_WITHOUT_RUNTIME)?.toBoolean()
    ?: System.getProperty("artifact.mac.no.jdk").toBoolean()

  /**
   * Relative paths to files in macOS distribution which should take 'executable' permissions.
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  // Method override handlers (stored as lambdas)
  private var copyAdditionalFilesHandler: (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = null
  private var rootDirectoryNameHandler: ((ApplicationInfoProperties, String) -> String)? = null
  private var customIdeaPropertiesHandler: ((ApplicationInfoProperties) -> Map<String, String>)? = null
  private var binariesToSignHandler: ((BuildContext, JvmArchitecture) -> List<String>)? = null
  private var distributionUUIDHandler: ((BuildContext, UUID?) -> UUID)? = null
  private var executableFilePatternsHandler: ((Sequence<String>, Boolean, JvmArchitecture, BuildContext) -> Sequence<String>)? = null

  /**
   * Gets the current copyAdditionalFiles handler for wrapping purposes.
   * @return the current handler, or null if none is set
   */
  fun getCopyAdditionalFilesHandler(): (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = copyAdditionalFilesHandler

  /**
   * Gets the current distributionUUID handler for checking if one is set.
   * @return the current handler, or null if none is set
   */
  fun getDistributionUUIDHandler(): ((BuildContext, UUID?) -> UUID)? = distributionUUIDHandler

  /**
   * Adds custom logic for copying additional files to the macOS distribution.
   * This handler is called after the base copyAdditionalFiles logic.
   *
   * @param handler Lambda receiving context, targetDir, and arch
   */
  fun copyAdditionalFiles(handler: suspend (targetDir: Path, arch: JvmArchitecture, context: BuildContext) -> Unit) {
    this.copyAdditionalFilesHandler = handler
  }

  /**
   * Sets a custom application bundle name (<name>.app).
   *
   * @param handler Lambda receiving ApplicationInfoProperties and buildNumber, returning the root directory name
   */
  fun rootDirectoryName(handler: (ApplicationInfoProperties, String) -> String) {
    this.rootDirectoryNameHandler = handler
  }

  /**
   * Sets custom properties to be added to bin/idea.properties file.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and returning a map of properties
   */
  fun customIdeaProperties(handler: (ApplicationInfoProperties) -> Map<String, String>) {
    this.customIdeaPropertiesHandler = handler
  }

  /**
   * Sets which binaries should be signed.
   *
   * @param handler Lambda receiving BuildContext and JvmArchitecture, returning a list of relative paths
   */
  fun binariesToSign(handler: (BuildContext, JvmArchitecture) -> List<String>) {
    this.binariesToSignHandler = handler
  }

  /**
   * Sets a custom UUID generator for the distribution.
   *
   * @param handler Lambda receiving BuildContext and current UUID, returning the distribution UUID
   */
  fun distributionUUID(handler: (BuildContext, UUID?) -> UUID) {
    this.distributionUUIDHandler = handler
  }

  /**
   * Sets custom executable file patterns generator.
   * The handler receives base patterns from the parent customizer, making it easy to extend or filter them.
   *
   * Example:
   * ```kotlin
   * executableFilePatterns { base, _, arch, _ ->
   *   base + listOf("bin/custom/$arch/tool")
   * }
   * ```
   *
   * @param handler Lambda receiving base patterns, includeRuntime flag, arch, and context, returning a sequence of patterns
   */
  fun executableFilePatterns(handler: (basePatterns: Sequence<String>, Boolean, JvmArchitecture, BuildContext) -> Sequence<String>) {
    this.executableFilePatternsHandler = handler
  }

  /**
   * Builds the [MacDistributionCustomizer] with the configured settings.
   * Automatically prefixes relative paths with projectHome.
   */
  fun build(): MacDistributionCustomizer {
    return MacDistributionCustomizerImpl(builder = this, projectHome = projectHome)
  }

  private class MacDistributionCustomizerImpl(
    private val builder: MacCustomizerBuilder,
    private val projectHome: Path,
  ) : MacDistributionCustomizer() {
    init {
      builder.icnsPath?.let { icnsPath = projectHome.resolve(it) }
      builder.icnsPathForEAP?.let { icnsPathForEAP = projectHome.resolve(it) }
      @Suppress("DEPRECATION")
      builder.icnsPathForAlternativeIcon?.let { icnsPathForAlternativeIcon = projectHome.resolve(it) }
      @Suppress("DEPRECATION")
      builder.icnsPathForAlternativeIconForEAP?.let { icnsPathForAlternativeIconForEAP = projectHome.resolve(it) }
      builder.bundleIdentifier?.let { bundleIdentifier = it }
      builder.dmgImagePath?.let { dmgImagePath = projectHome.resolve(it) }
      builder.dmgImagePathForEAP?.let { dmgImagePathForEAP = projectHome.resolve(it) }
      minOSXVersion = builder.minOSXVersion
      additionalDocTypes = builder.additionalDocTypes
      fileAssociations = builder.fileAssociations
      urlSchemes = builder.urlSchemes
      associateIpr = builder.associateIpr
      binFilesFilter = builder.binFilesFilter
      buildArtifactWithoutRuntime = builder.buildArtifactWithoutRuntime
      extraExecutables = builder.extraExecutables
    }

    override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
      super.copyAdditionalFiles(context = context, targetDir = targetDir, arch = arch)
      builder.copyAdditionalFilesHandler?.invoke(targetDir, arch, context)
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
      return builder.rootDirectoryNameHandler?.invoke(appInfo, buildNumber) ?: super.getRootDirectoryName(appInfo, buildNumber)
    }

    override fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> {
      return builder.customIdeaPropertiesHandler?.invoke(appInfo) ?: super.getCustomIdeaProperties(appInfo)
    }

    override fun getBinariesToSign(context: BuildContext, arch: JvmArchitecture): List<String> {
      return builder.binariesToSignHandler?.invoke(context, arch) ?: super.getBinariesToSign(context, arch)
    }

    override fun getDistributionUUID(context: BuildContext, currentUuid: UUID?): UUID {
      return builder.distributionUUIDHandler?.invoke(context, currentUuid)
        ?: super.getDistributionUUID(context, currentUuid)
    }

    override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, context: BuildContext): Sequence<String> {
      val basePatterns = super.generateExecutableFilesPatterns(includeRuntime, arch, context)
      return builder.executableFilePatternsHandler?.invoke(basePatterns, includeRuntime, arch, context)
        ?: basePatterns
    }
  }
}

/**
 * Creates a [MacDistributionCustomizer] with Community edition defaults using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * communityMacCustomizer(projectHome) {
 *   // Override or extend Community defaults
 *   urlSchemes += "myscheme"
 * }
 * ```
 */
inline fun communityMacCustomizer(projectHome: Path, configure: MacCustomizerBuilder.() -> Unit = {}): MacDistributionCustomizer {
  return macCustomizer(projectHome) {
    // Set Community defaults
    icnsPath = "build/conf/ideaCE/mac/images/idea.icns"
    icnsPathForEAP = "build/conf/ideaCE/mac/images/communityEAP.icns"
    urlSchemes = listOf("idea")
    associateIpr = true
    fileAssociations = FileAssociation.from("java", "groovy", "kt", "kts")
    bundleIdentifier = "com.jetbrains.intellij.ce"
    dmgImagePath = "build/conf/ideaCE/mac/images/dmg_background.tiff"
    
    rootDirectoryName { appInfo, buildNumber ->
      if (appInfo.isEAP) {
        "IntelliJ IDEA ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} CE EAP.app"
      }
      else {
        "IntelliJ IDEA CE.app"
      }
    }
    
    executableFilePatterns { base, includeRuntime, arch, context ->
      val kotlinExecutables = org.jetbrains.intellij.build.kotlin.KotlinBinaries.kotlinCompilerExecutables
      (base + kotlinExecutables).filterNot { it == "plugins/**/*.sh" }
    }
    
    // Apply user configuration
    configure()
  }
}

open class MacDistributionCustomizer {
  companion object {
    /**
     * Pass 'true' to this system property to produce additional .dmg and .sit archives for macOS without a runtime.
     */
    const val BUILD_ARTIFACT_WITHOUT_RUNTIME: String = "intellij.build.dmg.without.bundled.jre"
  }

  /**
   * A path to an .icns file containing product bundle icons for macOS distribution.
   *
   * Reference: [Apple Icon Image Format](https://en.wikipedia.org/wiki/Apple_Icon_Image_format).
   */
  var icnsPath: Path? = null

  /**
   * Path to an .icns file for EAP builds (if `null`, [icnsPath] will be used).
   */
  var icnsPathForEAP: Path? = null

  /**
   * Path to an alternative .icns file in macOS Big Sur style
   */
  @Deprecated("BigSur-style icons are now used by default")
  var icnsPathForAlternativeIcon: Path? = null

  /**
   * Path to an alternative .icns file in macOS Big Sur style for EAP
   */
  @Deprecated("BigSur-style icons are now used by default")
  var icnsPathForAlternativeIconForEAP: Path? = null

  /**
   * Relative paths to files in macOS distribution which should take 'executable' permissions.
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  /**
   * A unique identifier string that specifies the app type of the bundle.
   * The string should be in reverse DNS format using only the Roman alphabet in upper and lower case (A-Z, a-z), dots ('.'), and hyphens ('-').
   *
   * Reference:
   * [CFBundleIdentifier](https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundleidentifier).
   */
  lateinit var bundleIdentifier: String

  /**
   * Path to an image which will be injected into the .dmg file.
   */
  var dmgImagePath: Path? = null

  /**
   * The minimum version of macOS where the product is allowed to be installed.
   */
  var minOSXVersion: String = "10.13"

  /**
   * String with declarations of additional file types that should be automatically opened by the application.
   * Example:
   * ```
   * <dict>
   *   <key>CFBundleTypeExtensions</key>
   *   <array>
   *     <string>extension</string>
   *   </array>
   *   <key>CFBundleTypeIconFile</key>
   *   <string>path_to_icons.icns</string>
   *   <key>CFBundleTypeName</key>
   *   <string>File type description</string>
   *   <key>CFBundleTypeRole</key>
   *   <string>Editor</string>
   * </dict>
   * ```
   */
  var additionalDocTypes: String = ""

  /**
   * Note that users won't be able to switch off some of these associations during installation,
   * so include only types of files which users will definitely prefer to open by the product.
   *
   * @see FileAssociation
   */
  var fileAssociations: List<FileAssociation> = emptyList()

  /**
   * Specify a `<scheme>` here for the product to be able to open URIs like `<scheme>://open?file=/some/file/path&line=0`.
   */
  var urlSchemes: List<String> = emptyList()

  /**
   * If `true`, `*.ipr` files will be associated with the product in `Info.plist`.
   */
  var associateIpr: Boolean = false

  /**
   * Filter for files that is going to be put to `<distribution>/bin` directory.
   */
  var binFilesFilter: Predicate<Path> = Predicate { true }

  /**
   * Relative paths to files in macOS distribution which should be signed.
   */
  open fun getBinariesToSign(context: BuildContext, arch: JvmArchitecture): List<String> = listOf()

  /**
   * Path to an image which will be injected into .dmg file for EAP builds (if `null` dmgImagePath will be used).
   */
  var dmgImagePathForEAP: Path? = null

  /**
   * If `true`, a separate *-[org.jetbrains.intellij.build.impl.NO_RUNTIME_SUFFIX].dmg artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime: Boolean = System.getProperty(BUILD_ARTIFACT_WITHOUT_RUNTIME)?.toBoolean() ?: System.getProperty("artifact.mac.no.jdk").toBoolean()

  /**
   * Application bundle name (`<name>.app`).
   * A current convention is to have `ProductName.app` for releases and `ProductName Version EAP.app` for early access builds.
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} EAP" else ""
    return "${appInfo.fullProductName}${suffix}.app"
  }

  /**
   * Custom properties to be added to the `bin/idea.properties` file.
   */
  open fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> = emptyMap()

  /**
   * Override this method to copy additional files to the macOS distribution of the product.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    RepairUtilityBuilder.bundle(os = OsFamily.MACOS, arch = arch, distributionDir = targetDir, context = context)
  }

  open fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, context: BuildContext): Sequence<String> {
    val basePatterns = if (context.options.isLanguageServer) sequenceOf("bin/${context.productProperties.baseFileName}")
    else sequenceOf(
      "bin/*.sh",
      "plugins/**/*.sh",
      "bin/fsnotifier",
      "bin/printenv",
      "bin/restarter",
      "MacOS/*"
    )

    val rtPatterns = if (includeRuntime) {
      context.bundledRuntime.executableFilesPatterns(OsFamily.MACOS, context.productProperties.runtimeDistribution)
    }
    else {
      emptySequence()
    }

    val utilPatters = RepairUtilityBuilder.executableFilesPatterns(context)
    return basePatterns + rtPatterns + utilPatters + extraExecutables + context.getExtraExecutablePattern(OsFamily.MACOS)
  }

  /**
   * Generates a UUID to be used as an identity of an IDE Mach-O image.
   *
   * For a native macOS app, the Apple linker (ld) sets the build UUID based on a hash of the built code. But for an IDE it doesn't work that way.
   * For different IDEs, the source code can be different, but a [org.jetbrains.intellij.build.NativeBinaryDownloader.getLauncher] binary may be exactly the same.
   * So, the different IDEs may get the same UUIDs.
   * And according to [the technote](https://developer.apple.com/documentation/technotes/tn3178-checking-for-and-resolving-build-uuid-problems), this may lead to the troubles:
   * > Each distinct Mach-O image must have its own unique build UUID.
   * > If you have two apps with different bundle IDs and the same main executable UUID, you might encounter weird problems with those subsystems.
   * > For example, the network subsystem might apply constraints for one of your apps to the other app.
   */
  @ApiStatus.Internal
  open fun getDistributionUUID(context: BuildContext, currentUuid: UUID?): UUID {
    return UUID.nameUUIDFromBytes("${context.fullBuildNumber}-${context.options.buildDateInSeconds}".toByteArray())
  }
}
