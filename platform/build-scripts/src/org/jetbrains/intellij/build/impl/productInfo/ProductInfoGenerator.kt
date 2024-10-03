// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

const val PRODUCT_INFO_FILE_NAME: String = "product-info.json"

@OptIn(ExperimentalSerializationApi::class)
internal val jsonEncoder: Json by lazy {
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
internal fun generateProductInfoJson(
  relativePathToBin: String,
  builtinModules: BuiltinModulesFileData?,
  launch: List<ProductInfoLaunchData>,
  context: BuildContext,
): String {
  val appInfo = context.applicationInfo
  val jbrFlavors = if (launch.any { it.javaExecutablePath != null } && context.bundledRuntime.build.startsWith("17.")) {
    listOf(ProductFlavorData("jbr17"))
  }
  else {
    emptyList()
  }
  val productProperties = context.productProperties
  val productFlavors = productProperties.getProductFlavors(context).map { ProductFlavorData(it) }
  val json = ProductInfoData(
    name = appInfo.fullProductName,
    version = appInfo.fullVersion,
    versionSuffix = appInfo.versionSuffix,
    buildNumber = context.buildNumber,
    productCode = appInfo.productCode,
    envVarBaseName = productProperties.getEnvironmentVariableBaseName(appInfo),
    dataDirectoryName = context.systemSelector,
    svgIconPath = if (appInfo.svgRelativePath == null) null else "${relativePathToBin}/${productProperties.baseFileName}.svg",
    productVendor = appInfo.shortCompanyName,
    launch = launch,
    customProperties = productProperties.generateCustomPropertiesForProductInfo(),
    bundledPlugins = builtinModules?.plugins ?: emptyList(),
    fileExtensions = builtinModules?.fileExtensions ?: emptyList(),

    modules = (builtinModules?.layout?.asSequence() ?: emptySequence()).filter { it.kind == ProductInfoLayoutItemKind.pluginAlias }.map { it.name }.toList(),
    layout = builtinModules?.layout ?: emptyList(),

    flavors = jbrFlavors + productFlavors,
  )
  return jsonEncoder.encodeToString<ProductInfoData>(json)
}

internal fun writeProductInfoJson(targetFile: Path, json: String, context: BuildContext) {
  Files.createDirectories(targetFile.parent)
  Files.writeString(targetFile, json)
  Files.setLastModifiedTime(targetFile, FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
}

internal suspend fun generateJetBrainsClientLaunchData(
  arch: JvmArchitecture,
  os: OsFamily,
  ideContext: BuildContext,
  vmOptionsFilePath: (BuildContext) -> String
): CustomCommandLaunchData? {
  return createJetBrainsClientContextForLaunchers(ideContext)?.let { clientContext ->
    CustomCommandLaunchData(
      commands = listOf("thinClient", "thinClient-headless", "installFrontendPlugins"),
      vmOptionsFilePath = vmOptionsFilePath(clientContext),
      bootClassPathJarNames = clientContext.bootClassPathJarNames,
      additionalJvmArguments = clientContext.getAdditionalJvmArguments(os, arch) + ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS,
      mainClass = clientContext.ideMainClassName,
      envVarBaseName = "JETBRAINS_CLIENT",
      dataDirectoryName = clientContext.systemSelector,
    )
  }
}

/**
 * Describes the format of a JSON file containing meta-information about a product installation.
 * Must be consistent with the `product-info.schema.json` file.
 */
@Serializable
data class ProductInfoData(
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
  val layout: List<ProductInfoLayoutItem>,
)

@Serializable
data class ProductFlavorData(@JvmField val id: String)

@Serializable
data class ProductInfoLaunchData(
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
data class CustomCommandLaunchData(
  val commands: List<String>,
  val vmOptionsFilePath: String? = null,
  val bootClassPathJarNames: List<String> = emptyList(),
  val additionalJvmArguments: List<String> = emptyList(),
  val mainClass: String? = null,
  val envVarBaseName: String? = null,
  val dataDirectoryName: String? = null,
)

@Serializable
data class CustomProperty(
  val key: String,
  val value: String,
)
