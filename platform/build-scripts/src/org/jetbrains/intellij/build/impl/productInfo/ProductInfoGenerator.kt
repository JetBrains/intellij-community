// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuiltinModulesFileData

internal const val PRODUCT_INFO_FILE_NAME = "product-info.json"

@OptIn(ExperimentalSerializationApi::class)
internal val jsonEncoder by lazy {
  Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = false
    explicitNulls = false
  }
}

/**
 * Generates product-info.json file containing meta-information about product installation.
 */
internal fun generateMultiPlatformProductJson(relativePathToBin: String,
                                              builtinModules: BuiltinModulesFileData?,
                                              launch: List<ProductInfoLaunchData>,
                                              context: BuildContext): String {
  val appInfo = context.applicationInfo
  val json = ProductInfoData(
    name = appInfo.productName,
    version = appInfo.fullVersion,
    versionSuffix = appInfo.versionSuffix,
    buildNumber = context.buildNumber,
    productCode = appInfo.productCode,
    dataDirectoryName = context.systemSelector,
    svgIconPath = if (appInfo.svgRelativePath == null) null else "$relativePathToBin/${context.productProperties.baseFileName}.svg",
    productVendor = appInfo.shortCompanyName,
    launch = launch,
    customProperties = context.productProperties.generateCustomPropertiesForProductInfo(),
    bundledPlugins = builtinModules?.bundledPlugins ?: emptyList(),
    fileExtensions = builtinModules?.fileExtensions ?: emptyList(),
    modules = builtinModules?.modules ?: emptyList(),
  )

  return jsonEncoder.encodeToString(serializer(), json)
}

/**
 * Describes format of JSON file containing meta-information about a product installation. Must be consistent with 'product-info.schema.json' file.
 */
@Serializable
data class ProductInfoData(
  val name: String,
  val version: String,
  val versionSuffix: String?,
  val buildNumber: String,
  val productCode: String,
  val dataDirectoryName: String,
  val svgIconPath: String?,
  val productVendor: String,
  val launch: List<ProductInfoLaunchData>,
  val customProperties: List<CustomProperty> = emptyList(),
  val bundledPlugins: List<String>,
  val modules: List<String>,
  val fileExtensions: List<String>,
)

@Serializable
data class ProductInfoLaunchData(
  val os: String,
  val arch: String,
  val launcherPath: String,
  val javaExecutablePath: String?,
  val vmOptionsFilePath: String,
  val startupWmClass: String? = null,
  val bootClassPathJarNames: List<String>,
  val additionalJvmArguments: List<String>
)

@Serializable
data class CustomProperty(
  val key: String,
  val value: String,
)