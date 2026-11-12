// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.getMacBundleSuffix
import org.jetbrains.intellij.build.io.substituteTemplatePlaceholders
import org.jetbrains.intellij.build.isMacEapBundle
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

/**
 * The template of the `Contents` directory of a macOS app, relative to the community root: `Info.plist` and
 * `Resources/<language>.lproj/InfoPlist.strings`.
 */
@ApiStatus.Internal
const val MAC_APP_TEMPLATE_PATH: String = "platform/build-scripts/resources/mac/Contents"

/** The icon file of the macOS app in `Contents/Resources`, which `CFBundleIconFile` names. */
internal fun macAppIcnsFileName(baseFileName: String): String = "$baseFileName.icns"

/**
 * The `@@doc_types@@` value of `Info.plist`: the `ipr` entry when [MacDistributionCustomizer.associateIpr] is set, one
 * entry for each file association, then [MacDistributionCustomizer.additionalDocTypes]. [icnsFileName] is the icon of the
 * app, which an association without its own icon shows.
 */
internal fun macDocTypes(customizer: MacDistributionCustomizer, fullProductName: String, icnsFileName: String): String {
  val associations = mutableListOf<String>()

  if (customizer.associateIpr) {
    associations += """
      |<dict>
      |        <key>CFBundleTypeExtensions</key>
      |        <array>
      |          <string>ipr</string>
      |        </array>
      |        <key>CFBundleTypeIconFile</key>
      |        <string>${icnsFileName}</string>
      |        <key>CFBundleTypeName</key>
      |        <string>${fullProductName} Project File</string>
      |        <key>CFBundleTypeRole</key>
      |        <string>Editor</string>
      |      </dict>""".trimMargin()
  }

  for ((extension, iconPath) in customizer.fileAssociations) {
    associations += """
      |<dict>
      |        <key>CFBundleTypeExtensions</key>
      |        <array>
      |          <string>${extension}</string>
      |        </array>
      |        <key>CFBundleTypeIconFile</key>
      |        <string>${if (iconPath.isEmpty()) icnsFileName else Path.of(iconPath).name}</string>        
      |        <key>CFBundleTypeRole</key>
      |        <string>Editor</string>
      |      </dict>""".trimMargin()
  }

  return associations.joinToString(separator = "\n      ", postfix = customizer.additionalDocTypes)
}

/**
 * Fills the placeholders of `Info.plist` in [macAppDir] and of each `*.strings` file below its `Resources`.
 *
 * [docTypes] is the value of [macDocTypes]. [year] is the last year of the copyright line.
 */
@ApiStatus.Internal
fun substituteMacAppPlaceholders(
  macAppDir: Path,
  customizer: MacDistributionCustomizer,
  applicationInfo: ApplicationInfoProperties,
  fullBuildNumber: String,
  baseFileName: String,
  docTypes: String,
  arch: JvmArchitecture,
  year: String,
) {
  val fullName = applicationInfo.fullProductName
  //todo improve
  val minor = applicationInfo.minorVersion
  val isNotRelease = isMacEapBundle(applicationInfo)
  val version = if (isNotRelease) "EAP $fullBuildNumber" else "${applicationInfo.majorVersion}.${minor}"
  val isEap = getMacBundleSuffix(applicationInfo)
  val urlSchemes = customizer.urlSchemes
  val urlSchemesString = if (urlSchemes.isEmpty()) ""
  else """
    |<key>CFBundleURLTypes</key>
    |    <array>
    |      <dict>
    |        <key>CFBundleTypeRole</key>
    |        <string>Editor</string>
    |        <key>CFBundleURLName</key>
    |        <string>Stacktrace</string>
    |        <key>CFBundleURLSchemes</key>
    |        <array>
    |          ${urlSchemes.joinToString(separator = "\n          ") { "<string>${it}</string>" }}
    |        </array>
    |      </dict>
    |    </array>""".trimMargin()
  val architecture = when (arch) {
    JvmArchitecture.x64 -> "x86_64"
    JvmArchitecture.aarch64 -> "arm64"
  }

  substituteTemplatePlaceholders(
    inputFile = macAppDir.resolve("Info.plist"),
    outputFile = macAppDir.resolve("Info.plist"),
    placeholder = "@@",
    values = listOf(
      Pair("build", fullBuildNumber),
      Pair("doc_types", docTypes),
      Pair("executable", baseFileName),
      Pair("icns", macAppIcnsFileName(baseFileName)),
      Pair("bundle_name", fullName),
      Pair("product_state", isEap),
      Pair("bundle_identifier", customizer.bundleIdentifier),
      Pair("year", year),
      Pair("version", version),
      Pair("url_schemes", urlSchemesString),
      Pair("architecture", architecture),
      Pair("min_osx", customizer.minOSXVersion),
    )
  )

  macAppDir.resolve("Resources").walk()
    .filter { it.extension == "strings" && it.isRegularFile() }
    .forEach { file ->
      substituteTemplatePlaceholders(
        inputFile = file,
        outputFile = file,
        placeholder = "@@",
        values = listOf(
          Pair("bundle_name", fullName),
          Pair("version", version),
          Pair("year", year),
          Pair("build", fullBuildNumber)
        )
      )
    }
}

/**
 * The icons that the macOS app of [properties] holds in `Contents/Resources`, keyed by file name.
 *
 * They are the icon of the app, the alternative icon, the icon of the embedded frontend when [embeddedFrontendEnabled]
 * is set, then the icon of each file association that states one. A later icon with the same name replaces an
 * earlier one. [isEap] selects the EAP variant of an icon.
 */
@ApiStatus.Internal
fun macAppIcons(
  customizer: MacDistributionCustomizer,
  properties: ProductProperties,
  isEap: Boolean,
  embeddedFrontendEnabled: Boolean,
): Map<String, Path> {
  val result = LinkedHashMap<String, Path>()
  result.put(macAppIcnsFileName(properties.baseFileName), locateIcnsForMacApp(customizer, properties, isEap))
  @Suppress("DEPRECATION")
  val alternativeIcon = customizer.icnsPathForAlternativeIconForEAP?.takeIf { isEap } ?: customizer.icnsPathForAlternativeIcon
  if (alternativeIcon != null) {
    result.put("custom.icns", alternativeIcon)
  }
  if (embeddedFrontendEnabled) {
    //path to the copied file will be passed as the value of `apple.awt.application.icon` property in `getAdditionalEmbeddedClientVmOptions`
    locateIcnsForFrontendMacApp(properties.imagesDirectoryPath, isEap)?.let { result.put("frontend.icns", it) }
  }
  for ((_, iconPath) in customizer.fileAssociations) {
    if (!iconPath.isEmpty()) {
      val source = Path.of(iconPath)
      result.remove(source.name)
      result.put(source.name, source)
    }
  }
  return result
}
