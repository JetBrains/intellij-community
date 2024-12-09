// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildData.productInfo

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Describes the format of a JSON file containing meta-information about a product installation.
 * Must be consistent with the `product-info.schema.json` file.
 */
@Serializable
class ProductInfoData @ApiStatus.Internal constructor(
  val name: String,
  val version: String,
  val versionSuffix: String?,
  val buildNumber: String,
  val productCode: String,
  val envVarBaseName: String,
  val dataDirectoryName: String,
  val svgIconPath: String?,
  val productVendor: String,
  val launch: List<ProductInfoLaunchData>,
  val customProperties: List<CustomProperty> = emptyList(),
  val bundledPlugins: List<String>,
  // it is not modules, but plugin aliases
  val modules: List<String>,
  val fileExtensions: List<String>,
  val flavors: List<ProductFlavorData> = emptyList(),

  
  // not used by launcher, specify in the end
  @ApiStatus.Internal
  val layout: List<ProductInfoLayoutItem>,
)

@Serializable
class ProductFlavorData @ApiStatus.Internal constructor(@JvmField val id: String)

@Serializable
class ProductInfoLaunchData @ApiStatus.Internal constructor(
  val os: String,
  val arch: String,
  val launcherPath: String,
  val javaExecutablePath: String?,
  val vmOptionsFilePath: String,
  val startupWmClass: String? = null,
  val bootClassPathJarNames: List<String>,
  val additionalJvmArguments: List<String>,
  val mainClass: String,
  val customCommands: List<CustomCommandLaunchData> = emptyList(),
)

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