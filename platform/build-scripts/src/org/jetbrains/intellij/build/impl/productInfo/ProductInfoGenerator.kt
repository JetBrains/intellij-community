// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.buildData.productInfo.*
import kotlinx.serialization.ExperimentalSerializationApi
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
  val json = ProductInfoData.create(
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

