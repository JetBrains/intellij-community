// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path
import java.util.function.Predicate

abstract class MacDistributionCustomizer {
  companion object {
    /**
     * Pass 'true' to this system property to produce an additional .dmg and .sit archives for macOS without Runtime.
     */
    const val BUILD_ARTIFACT_WITHOUT_RUNTIME = "intellij.build.dmg.without.bundled.jre"
  }

  /**
   * A path to an .icns file containing product bundle icons for macOS distribution.
   *
   * Reference: [Apple Icon Image Format](https://en.wikipedia.org/wiki/Apple_Icon_Image_format).
   */
  lateinit var icnsPath: String

  /**
   * Path to icns file for EAP builds (if `null`, [icnsPath] will be used).
   */
  var icnsPathForEAP: String? = null

  /**
   * Path to alternative icns file in macOS Big Sur style
   */
  var icnsPathForAlternativeIcon: String? = null

  /**
   * Path to alternative icns file in macOS Big Sur style for EAP
   */
  var icnsPathForAlternativeIconForEAP: String? = null

  /**
   * Relative paths to files in macOS distribution which should take 'executable' permissions.
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  /**
   * A unique identifier string that specifies the app type of the bundle.
   * The string should be in reverse DNS format using only the Roman alphabet in upper and lower case (A-Z, a-z), the dot ("."),
   * and the hyphen ("-").
   *
   * Reference:
   * [CFBundleIdentifier](https://developer.apple.com/library/ios/documentation/General/Reference/InfoPlistKeyReference/Articles/CoreFoundationKeys.html#//apple_ref/doc/uid/20001431-102070).
   */
  lateinit var bundleIdentifier: String

  /**
   * Path to an image which will be injected into .dmg file.
   */
  lateinit var dmgImagePath: String

  /**
   * The minimum version of macOS where the product is allowed to be installed.
   */
  var minOSXVersion = "10.13"

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
  var additionalDocTypes = ""

  /**
   * Note that users won't be able to switch off some of these associations during installation
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
  var associateIpr = false

  /**
   * Enables the use of the new cross-platform launcher (which loads launch data from `product-info.json` instead of `Info.plist`).
   */
  var useXPlatLauncher = true

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
  var dmgImagePathForEAP: String? = null

  /**
   * If `true`, a separate *-[org.jetbrains.intellij.build.impl.MacDistributionBuilder.NO_RUNTIME_SUFFIX].dmg artifact without a runtime will be produced.
   */
  var buildArtifactWithoutRuntime = SystemProperties.getBooleanProperty(BUILD_ARTIFACT_WITHOUT_RUNTIME,
                                                                        SystemProperties.getBooleanProperty(
                                                                          "artifact.mac.no.jdk",
                                                                          false))

  /**
   * Application bundle name (`<name>.app`).
   * A current convention is to have `ProductName.app` for releases and `ProductName Version EAP.app` for early access builds.
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} EAP" else ""
    return "${appInfo.productName}${suffix}.app"
  }

  /**
   * Custom properties to be added to the `bin/idea.properties` file.
   */
  open fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> = emptyMap()

  @Deprecated("Please migrate the build script to Kotlin and override `copyAdditionalFiles`")
  open fun copyAdditionalFiles(context: BuildContext, targetDir: Path) { }

  /**
   * Override this method to copy additional files to the macOS distribution of the product.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    @Suppress("DEPRECATION")
    copyAdditionalFiles(context, targetDir)
    RepairUtilityBuilder.bundle(context, OsFamily.MACOS, arch, targetDir)
  }

  open fun generateExecutableFilesPatterns(context: BuildContext, includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
    val basePatterns = persistentListOf(
      "bin/*.sh",
      "plugins/**/*.sh",
      "bin/fsnotifier",
      "bin/printenv",
      "bin/restarter",
      "MacOS/*"
    )

    val rtPatterns =
      if (includeRuntime) context.bundledRuntime.executableFilesPatterns(OsFamily.MACOS, context.productProperties.runtimeDistribution)
      else emptyList()

    return basePatterns +
           rtPatterns +
           RepairUtilityBuilder.executableFilesPatterns(context) +
           extraExecutables +
           context.getExtraExecutablePattern(OsFamily.MACOS)
  }
}
