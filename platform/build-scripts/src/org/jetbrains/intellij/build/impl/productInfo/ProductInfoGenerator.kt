// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProductInfoGenerator")
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import com.intellij.platform.buildData.productInfo.CustomProperty
import com.intellij.platform.buildData.productInfo.CustomPropertyNames
import com.intellij.platform.buildData.productInfo.ProductFlavorData
import com.intellij.platform.buildData.productInfo.ProductInfoData
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import com.intellij.platform.buildData.productInfo.ProductInfoLayoutItemKind
import kotlinx.serialization.ExperimentalSerializationApi
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

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
    majorVersionReleaseDate = LocalDate.parse(appInfo.majorReleaseDate, DateTimeFormatter.ofPattern("yyyyMMdd")),
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
  if (!context.options.storeGitRevision) {
    return null
  }
  
  val gitRoot = findGitRoot(context)
  if (gitRoot == null) {
    if (!context.options.isInDevelopmentMode && !context.options.isTestBuild) {
      context.messages.error("Cannot find Git repository root for '${context.paths.projectHome}'")
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

private fun findGitRoot(context: BuildContext): Path? {
  var projectHome: Path? = context.paths.projectHome
  // we check only for the existence of .git, because in the case of an additional worktree (git worktree add) it's actually a reference, not a folder.
  while (projectHome != null && !projectHome.resolve(".git").exists()) {
    projectHome = projectHome.parent
  }
  return projectHome
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
