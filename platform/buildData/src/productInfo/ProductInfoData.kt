// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildData.productInfo

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Describes the product-info file containing meta-information about a product installation.
 * The schema of the file is also described in the `product-info.schema.json` file, so it must be kept consistent with it.
 * 
 * Data from product-info.json is used from code inside the IDE it's bundled with (e.g., XPlatLauncher), and such internal clients may
 * rely on the presence of properties added in the current version.
 * Also, it's used by external tools and from other IDE versions, so the schema and deserialization logic must be backward compatible.
 * It means that all newly added properties must be optional, and default values must be provided for them in the primary constructor.
 * If the new version always specifies values for these properties in the product-info.json, they can be marked as non-null in 
 * [the factory method][ProductInfoData.create] to state that they are required in the current version and allow internal clients relying
 * on their presence.
 */
@Serializable
class ProductInfoData private constructor(
  val name: String,
  val version: String,
  val versionSuffix: String? = null,
  val buildNumber: String,
  val productCode: String,
  val envVarBaseName: String? = null,
  val dataDirectoryName: String? = null,
  val svgIconPath: String? = null,
  val productVendor: String? = null,
  val launch: List<ProductInfoLaunchData>,
  val customProperties: List<CustomProperty> = emptyList(),
  val bundledPlugins: List<String> = emptyList(),
  val modules: List<String> = emptyList(),  // actually, it is not modules but plugin aliases
  val fileExtensions: List<String> = emptyList(),
  val flavors: List<ProductFlavorData> = emptyList(),
  
  // not used by the launcher; must be at the end
  @ApiStatus.Internal
  val layout: List<ProductInfoLayoutItem> = emptyList(),
) {
  companion object {
    /**
     * Creates an instance of [ProductInfoData] for the current version.
     * Some properties that are nullable in the primary constructor are deliberately marked as not-null in this function to state that they
     * are required in the current version, and internal clients may rely on their presence.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun create(
      name: String,
      version: String,
      versionSuffix: String?,
      buildNumber: String,
      productCode: String,
      envVarBaseName: String,
      dataDirectoryName: String,
      svgIconPath: String?,
      productVendor: String,
      launch: List<ProductInfoLaunchData>,
      customProperties: List<CustomProperty>,
      bundledPlugins: List<String>,
      modules: List<String>,
      fileExtensions: List<String>,
      flavors: List<ProductFlavorData>,
      layout: List<ProductInfoLayoutItem>,
    ): ProductInfoData = ProductInfoData(
      name, version, versionSuffix, buildNumber, productCode, envVarBaseName, dataDirectoryName, svgIconPath, productVendor, launch,
      customProperties, bundledPlugins, modules, fileExtensions, flavors, layout
    )
  }
}

@Serializable
class ProductFlavorData @ApiStatus.Internal constructor(@JvmField val id: String)

/**
 * Describes 'launch' section in [product-info.json][ProductInfoData] file.
 */
@Serializable
class ProductInfoLaunchData private constructor(
  val os: String,
  val arch: String? = null,
  val launcherPath: String,
  val javaExecutablePath: String? = null,
  val vmOptionsFilePath: String,
  val startupWmClass: String? = null,
  val bootClassPathJarNames: List<String> = emptyList(),
  val additionalJvmArguments: List<String> = emptyList(),
  val mainClass: String? = null,
  val customCommands: List<CustomCommandLaunchData> = emptyList(),
) {
  companion object {
    /**
     * Creates an instance of [ProductInfoLaunchData] for the current version.
     * Some properties that are nullable in the primary constructor are deliberately marked as not-null in this function to state that they
     * are required in the current version, and internal clients may rely on their presence.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun create(
      os: String,
      arch: String,
      launcherPath: String,
      javaExecutablePath: String?,
      vmOptionsFilePath: String,
      bootClassPathJarNames: List<String>,
      additionalJvmArguments: List<String>,
      mainClass: String,
      startupWmClass: String? = null,
      customCommands: List<CustomCommandLaunchData> = emptyList(),
    ): ProductInfoLaunchData = ProductInfoLaunchData(
      os, arch, launcherPath, javaExecutablePath, vmOptionsFilePath, startupWmClass, bootClassPathJarNames, additionalJvmArguments,
      mainClass, customCommands
    )
  }
}

@Serializable
class CustomCommandLaunchData @ApiStatus.Internal constructor(
  val commands: List<String>,
  val vmOptionsFilePath: String? = null,
  val bootClassPathJarNames: List<String> = emptyList(),
  val additionalJvmArguments: List<String> = emptyList(),
  val mainClass: String? = null,
  val envVarBaseName: String? = null,
  val dataDirectoryName: String? = null,
)

@Serializable
class CustomProperty @ApiStatus.Internal constructor(
  val key: String,
  val value: String,
)
