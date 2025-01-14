// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.buildData.productInfo.*
import com.jetbrains.plugin.structure.base.utils.isDirectory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.Git
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createFrontendContextForLaunchers
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
 * Generates a `product-info.json` file describing product installation.
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
    customProperties = listOfNotNull(generateGitRevisionProperty(context)) + productProperties.generateCustomPropertiesForProductInfo(),
    bundledPlugins = builtinModules?.plugins ?: emptyList(),
    fileExtensions = builtinModules?.fileExtensions ?: emptyList(),
    modules = (builtinModules?.layout?.asSequence() ?: emptySequence()).filter { it.kind == ProductInfoLayoutItemKind.pluginAlias }.map { it.name }.toList(),
    layout = builtinModules?.layout ?: emptyList(),
    flavors = jbrFlavors + productFlavors,
  )
  return jsonEncoder.encodeToString<ProductInfoData>(json)
}

private fun generateGitRevisionProperty(context: BuildContext): CustomProperty? {
  val gitRoot = context.paths.projectHome
  if (!gitRoot.resolve(".git").isDirectory) {
    if (!context.options.isInDevelopmentMode && !context.options.isTestBuild) {
      context.messages.error("Cannot find Git repository root in '$gitRoot'")
    }
    return null
  }
  try {
    val revision = Git(gitRoot).currentCommitShortHash()
    return CustomProperty(CustomPropertyNames.GIT_REVISION, revision)
  }
  catch (e: Exception) {
    context.messages.error("Cannot determine Git revision to store in product-info.json: ${e.message}", e)
    return null
  }
}

internal fun writeProductInfoJson(targetFile: Path, json: String, context: BuildContext) {
  Files.createDirectories(targetFile.parent)
  Files.writeString(targetFile, json)
  Files.setLastModifiedTime(targetFile, FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS))
}

internal suspend fun generateEmbeddedFrontendLaunchData(
  arch: JvmArchitecture,
  os: OsFamily,
  ideContext: BuildContext,
  vmOptionsFilePath: (BuildContext) -> String
): CustomCommandLaunchData? {
  return createFrontendContextForLaunchers(ideContext)?.let { clientContext ->
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
