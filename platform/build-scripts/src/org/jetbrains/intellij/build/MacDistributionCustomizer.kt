// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path
import java.util.function.Predicate

abstract class MacDistributionCustomizer {
  /**
   * Path to icns file containing product icon bundle for macOS distribution
   * For full description of icns files see <a href="https://en.wikipedia.org/wiki/Apple_Icon_Image_format">Apple Icon Image Format</a>
   */
  lateinit var icnsPath: String

  /**
   * Path to icns file for EAP builds (if `null` [icnsPath] will be used)
   */
  var icnsPathForEAP: String? = null

  /**
   * Relative paths to files in macOS distribution which should take 'executable' permissions
   */
  var extraExecutables: PersistentList<String> = persistentListOf()

  /**
   * A unique identifier string that specifies the app type of the bundle. The string should be in reverse DNS format using only the Roman alphabet in upper and lower case (A-Z, a-z), the dot ("."), and the hyphen ("-")
   * See <a href="https://developer.apple.com/library/ios/documentation/General/Reference/InfoPlistKeyReference/Articles/CoreFoundationKeys.html#//apple_ref/doc/uid/20001431-102070">CFBundleIdentifier</a> for details
   */
  lateinit var bundleIdentifier: String

  /**
   * Path to an image which will be injected into .dmg file
   */
  lateinit var dmgImagePath: String

  /**
   * The minimum version of macOS where the product is allowed to be installed
   */
  var minOSXVersion = "10.15"

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
   * Specify &lt;scheme&gt; here if you want product to be able to open urls like <scheme>://open?file=/some/file/path&line=0
   */
  var urlSchemes: List<String> = emptyList()

  /**
   * If `true` *.ipr files will be associated with the product in Info.plist
   */
  var associateIpr = false

  /**
   * Enables the use of the new cross-platform launcher (which loads launch data from `product-info.json` instead of `Info.plist`).
   */
  var useXPlatLauncher = false

  /**
   * Filter for files that is going to be put to `<distribution>/bin` directory.
   */
  var binFilesFilter: Predicate<Path> = Predicate { true }

  /**
   * Relative paths to files in macOS distribution which should be signed
   */
  open fun getBinariesToSign(context: BuildContext, arch: JvmArchitecture): List<String> = listOf()

  /**
   * Path to an image which will be injected into .dmg file for EAP builds (if `null` dmgImagePath will be used)
   */
  var dmgImagePathForEAP: String? = null

  /**
   * Application bundle name: &lt;name&gt;.app. Current convention is to have ProductName.app for release and ProductName Version EAP.app.
   * @param appInfo application info that can be used to check for EAP and building version
   * @param buildNumber current build number
   * @return application bundle directory name
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    val suffix = if (appInfo.isEAP) " ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} EAP" else ""
    return "${appInfo.productName}${suffix}.app"
  }

  /**
   * Custom properties to be added to the properties file. They will be used for launched product, e.g. you can add additional logging in EAP builds
   * @param appInfo application info that can be used to check for EAP and building version
   * @return map propertyName-&gt;propertyValue
   */
  open fun getCustomIdeaProperties(appInfo: ApplicationInfoProperties): Map<String, String> = emptyMap()

  /**
   * Additional files to be copied to the distribution, e.g. help bundle or debugger binaries
   *
   * @param context build context that contains information about build directories, product properties and application info
   * @param targetDirectory application bundle directory
   */
  open fun copyAdditionalFiles(context: BuildContext, targetDirectory: Path) {
    copyAdditionalFilesBlocking(context, targetDirectory)
  }

  protected open fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path) {
  }

  /**
   * Additional files to be copied to the distribution with specific architecture, e.g. help bundle or debugger binaries
   *
   * Method is invoked after [copyAdditionalFiles].
   * In this method invocation `targetDirectory` may be different from in aforementioned method and may contain nothing.
   *
   * @param context build context that contains information about build directories, product properties and application info
   * @param targetDirectory application bundle directory
   * @param arch distribution target architecture, not null
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: Path, arch: JvmArchitecture) {
    RepairUtilityBuilder.bundle(context, OsFamily.MACOS, arch, targetDirectory)
    copyAdditionalFilesBlocking(context, targetDirectory, arch)
  }

  protected open fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path, arch: JvmArchitecture) {
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
