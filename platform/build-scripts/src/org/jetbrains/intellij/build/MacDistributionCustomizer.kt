// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

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
  lateinit var icnsPath: String

  /**
   * Path to an .icns file for EAP builds (if `null`, [icnsPath] will be used).
   */
  var icnsPathForEAP: String? = null

  /**
   * Path to an alternative .icns file in macOS Big Sur style
   */
  var icnsPathForAlternativeIcon: String? = null

  /**
   * Path to an alternative .icns file in macOS Big Sur style for EAP
   */
  var icnsPathForAlternativeIconForEAP: String? = null

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
  lateinit var dmgImagePath: String

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
  var dmgImagePathForEAP: String? = null

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
    RepairUtilityBuilder.bundle(context, OsFamily.MACOS, arch, targetDir)
  }

  open fun generateExecutableFilesPatterns(context: BuildContext, includeRuntime: Boolean, arch: JvmArchitecture): Sequence<String> {
    val basePatterns = sequenceOf(
      "bin/*.sh",
      "plugins/**/*.sh",
      "bin/fsnotifier",
      "bin/printenv",
      "bin/restarter",
      "MacOS/*"
    )

    val rtPatterns =
      if (includeRuntime) context.bundledRuntime.executableFilesPatterns(OsFamily.MACOS, context.productProperties.runtimeDistribution)
      else emptySequence()

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
  open fun getDistributionUUID(context: BuildContext, currentUuid: UUID?): UUID =
    UUID.nameUUIDFromBytes("${context.fullBuildNumber}-${context.options.buildDateInSeconds}".toByteArray())
}
