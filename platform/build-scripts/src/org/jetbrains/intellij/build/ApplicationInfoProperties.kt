// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal

interface ApplicationInfoProperties {
  val majorVersion: String
  val minorVersion: String
  val microVersion: String
  val patchVersion: String
  val fullVersionFormat: String
  val isEAP: Boolean
  val versionSuffix: String?
  /**
   * The first number from 'minor' part of the version. This property is temporary added because some products specify composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   */
  val minorVersionMainPart: String
  val shortProductName: String
  val productCode: String
  val fullProductName: String
  val majorReleaseDate: String
  val releaseVersionForLicensing: String
  val edition: String?
  val motto: String?
  val companyName: String
  val shortCompanyName: String
  val svgRelativePath: String?
  val svgProductIcons: List<String>

  val fullVersion: String
  val productNameWithEdition: String

  val launcherName: String
}

/**
 * Loads the instance of [ApplicationInfoProperties] for the provided product. Use this method only if you need to load the properties for
 * another product, to get the instance of the product currently being build, use [BuildContext.applicationInfo] instead.
 */
@Internal
fun BuildContext.loadApplicationInfoPropertiesForProduct(productProperties: ProductProperties): ApplicationInfoProperties {
  return ApplicationInfoPropertiesImpl(project, productProperties, options)
}