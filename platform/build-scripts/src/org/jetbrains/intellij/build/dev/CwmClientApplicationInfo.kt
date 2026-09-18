// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Namespace
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.applyApplicationInfoOverrides
import org.jetbrains.intellij.build.impl.BuildUtils
import java.nio.file.Files
import java.nio.file.Path

/**
 * The application info of the embedded JetBrains Client, from declared files and nothing else.
 *
 * The production layout reads the same values from a `BuildContext` copy of the client product. This function reads
 * the client template, the product application info and the build number as files, so the
 * `dev_dist_frontend_application_info` action runs it without a project model. The build number becomes `JBC-<build>`,
 * and the product values override the names, the version and the release date, as [applyApplicationInfoOverrides]
 * does for a product build.
 *
 * [branchName] is stamped for a nightly build and for a build number with at most one dot, the rule of the product
 * build. Every dev build leaves the four options at their defaults.
 */
@Suppress("DEPRECATION")
internal fun prepareCwmClientApplicationInfo(
  clientFile: Path,
  productFile: Path,
  buildNumberFile: Path,
  isEapOverride: String? = null,
  versionSuffixOverride: String? = null,
  nightlyBuild: Boolean = false,
  branchName: String? = null,
): String {
  val buildNumber = Files.readString(buildNumberFile).trim()
  require(buildNumber.isNotEmpty()) { "The CWM frontend build number is empty: $buildNumberFile" }
  val replaced = BuildUtils.replaceAll(
    text = Files.readString(clientFile),
    replacements = mapOf(
      "BUILD_NUMBER" to "JBC-$buildNumber",
      "BUILD" to buildNumber,
      "BUILTIN_PLUGINS_URL" to "",
    ),
    marker = "__",
  )
  return applyApplicationInfoOverrides(
    originalPatchedAppInfo = replaced,
    isEapOverride = isEapOverride,
    suffixOverride = versionSuffixOverride,
    appInfoXmlPath = clientFile,
    appInfoOverride = loadProductApplicationInfoOverrides(productFile),
    branchName = branchName.takeIf { nightlyBuild || buildNumber.count { character -> character == '.' } <= 1 },
  )
}

@Suppress("DEPRECATION")
private fun loadProductApplicationInfoOverrides(file: Path): ProductProperties.ApplicationInfoOverrides {
  val root = JDOMUtil.load(file)

  @Suppress("HttpUrlsUsage")
  val namespace = Namespace.getNamespace("http://jetbrains.org/intellij/schema/application-info")
  val names = root.getChildren("names", namespace).singleOrNull() ?: error("The product application info has no unique names element: $file")
  val version = root.getChildren("version", namespace).singleOrNull() ?: error("The product application info has no unique version element: $file")
  val build = root.getChildren("build", namespace).singleOrNull() ?: error("The product application info has no unique build element: $file")
  return ProductProperties.ApplicationInfoOverrides(
    fullProductName = names.getAttributeValue("fullname") ?: names.getAttributeValue("product")
                      ?: error("The product application info has no product name: $file"),
    editionName = null,
    motto = names.getAttributeValue("motto"),
    eap = version.getAttributeValue("eap"),
    majorVersion = version.getAttributeValue("major"),
    minorVersion = version.getAttributeValue("minor"),
    microVersion = version.getAttributeValue("micro"),
    patchVersion = version.getAttributeValue("patch"),
    fullVersionFormat = version.getAttributeValue("full"),
    versionSuffix = version.getAttributeValue("suffix"),
    majorReleaseDate = build.getAttributeValue("majorReleaseDate"),
  )
}
