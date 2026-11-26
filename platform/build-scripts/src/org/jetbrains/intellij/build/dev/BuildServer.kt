// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.classPath.launchBootClassPathJarNames
import org.jetbrains.intellij.build.classPath.splitDistributionCoreClassPathJarNames
import org.jetbrains.intellij.build.impl.MAC_APP_TEMPLATE_PATH
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.macAppIcnsFileName
import org.jetbrains.intellij.build.impl.macAppIcons
import org.jetbrains.intellij.build.impl.macDocTypes
import org.jetbrains.intellij.build.impl.productInfo.ProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.computeDevProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.devMacAppIcons
import org.jetbrains.intellij.build.impl.substituteMacAppPlaceholders
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Custom path for product properties
 */
private const val CUSTOM_PRODUCT_PROPERTIES_PATH = "idea.product.properties.path"

fun buildProductInProcess(request: BuildRequest): DevBuildResult {
  request.tracer?.let {
    TraceManager.setTracer(it)
  }
  return TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).use {
    val buildOptionsTemplate = BuildOptions()
    val configuration = createConfiguration(homePath = request.projectDir)
    val productConfiguration = getProductConfiguration(configuration, request.platformPrefix, request.baseIdePlatformPrefixForFrontend)
    buildProductFromProject(request = request, productConfiguration = productConfiguration, buildOptionsTemplate = buildOptionsTemplate)
  }
}

/**
 * What the production writers write for the `platform_resources` component of one product: [files] for each platform,
 * keyed by the path in the distribution, and the [model] the plan generator computes for the product.
 *
 * [coreClassPathJarNames] is the core classpath of the split distribution, see [splitDistributionCoreClassPathJarNames].
 * It is `null` for a modular-loader product, whose launch does not read it.
 *
 * [macBundleFiles] holds `Info.plist` and the `InfoPlist.strings` files of each macOS platform, keyed by the path
 * below `Contents`. [macAppIcons] holds the icon that the production writer copies into `Contents/Resources`, keyed by
 * file name. [macAppIconSources] holds the release and the EAP icon of each name, as the plan names them. All three
 * are empty for a product without a macOS app.
 */
@ApiStatus.Internal
class SplitPlatformResources(
  @JvmField val model: ProductLaunchModel,
  @JvmField val files: Map<Pair<OsFamily, JvmArchitecture>, Map<String, String>>,
  @JvmField val coreClassPathJarNames: List<String>?,
  @JvmField val macBundleFiles: Map<Pair<OsFamily, JvmArchitecture>, Map<String, String>>,
  @JvmField val macAppIcons: Map<String, Path>,
  @JvmField val macAppIconSources: Map<String, Pair<Path, Path>>,
)

/**
 * Renders the files of the `platform_resources` component of [platformPrefix] for each of [platforms] with the
 * production writers.
 *
 * The files come from `writePlatformResourceFiles` over a build context with no additional modules, no runtime module
 * repository, and the pinned [buildDateInSeconds]. The launch model tests compare them with the renderers of [model].
 * [scratchDir] must be empty.
 *
 * No jar is packed, so `generateCoreClassPath` does not run. For a classic-loader product, the function sets the boot
 * class path of the context from the core classpath of the split distribution, with the production rule.
 */
@ApiStatus.Internal
fun renderSplitPlatformResources(
  projectDir: Path,
  platformPrefix: String,
  buildDateInSeconds: Long,
  platforms: List<Pair<OsFamily, JvmArchitecture>>,
  scratchDir: Path,
): SplitPlatformResources {
  val productConfiguration = getProductConfiguration(createConfiguration(projectDir), platformPrefix, baseIdePlatformPrefixForFrontend = null)
  val request = BuildRequest(
    platformPrefix = platformPrefix,
    additionalModules = emptyList(),
    projectDir = projectDir,
    jarCacheDir = null,
    isBootClassPathCorrect = false,
    runDirOverride = scratchDir.resolve("run"),
    scratchDir = scratchDir.resolve("scratch"),
    buildDateInSeconds = buildDateInSeconds,
    fragment = DevBuildFragment.PLATFORM_RESOURCES,
  )
  BuildLifetime().use { lifetime ->
    val context = createBuildContextFromProject(
      productConfiguration = productConfiguration,
      request = request,
      buildDir = Files.createDirectories(scratchDir.resolve("run")),
      buildOptionsTemplate = BuildOptions(),
      lifetime = lifetime,
    )
    val coreClassPathJarNames = if (context.useModularLoader) {
      null
    }
    else {
      splitDistributionCoreClassPathJarNames(createPlatformLayout(context.productProperties, context.outputProvider))
    }
    if (coreClassPathJarNames != null) {
      context.bootClassPathJarNames = launchBootClassPathJarNames(
        coreClassPathJarNames = coreClassPathJarNames,
        skipNioFs = isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix),
      )
    }
    val files = platforms.associateWith { (os, arch) ->
      configureTargetPlatform(context.options, request.copy(os = os, arch = arch))
      val runDir = Files.createDirectories(scratchDir.resolve("${os.osId}-${arch.name}"))
      writePlatformResourceFiles(context, os, arch, runDir).associate { runDir.relativize(it).invariantSeparatorsPathString to Files.readString(it) }
    }
    // A dev build context has an empty macOS customizer, so the customizer comes from the product, as a release build
    // context creates it.
    val macCustomizer = context.productProperties.createMacCustomizer(context.paths.projectHome)
    val model = computeDevProductLaunchModel(context.productProperties, context.outputProvider, buildDateInSeconds, macCustomizer)
    val macBundleFiles = if (model.macBundle == null || macCustomizer == null) {
      emptyMap()
    }
    else {
      // The year of the pinned build date, in UTC, as the product files action takes it.
      val year = Instant.ofEpochSecond(buildDateInSeconds).atZone(ZoneOffset.UTC).year.toString()
      val baseFileName = context.productProperties.baseFileName
      val docTypes = macDocTypes(macCustomizer, context.applicationInfo.fullProductName, macAppIcnsFileName(baseFileName))
      platforms.filter { it.first == OsFamily.MACOS }.associateWith { (_, arch) ->
        val appDir = scratchDir.resolve("mac-app-${arch.name}")
        copyDir(context.paths.communityHomeDir.resolve(MAC_APP_TEMPLATE_PATH), appDir)
        substituteMacAppPlaceholders(
          macAppDir = appDir,
          customizer = macCustomizer,
          applicationInfo = context.applicationInfo,
          fullBuildNumber = context.fullBuildNumber,
          baseFileName = baseFileName,
          docTypes = docTypes,
          arch = arch,
          year = year,
        )
        Files.walk(appDir).use { paths ->
          paths.filter { Files.isRegularFile(it) }.toList().associate {
            appDir.relativize(it).invariantSeparatorsPathString to Files.readString(it)
          }
        }
      }
    }
    val hasMacApp = model.macBundle != null && macCustomizer != null
    val macAppIcons = if (!hasMacApp) {
      emptyMap()
    }
    else {
      macAppIcons(
        customizer = macCustomizer,
        properties = context.productProperties,
        isEap = context.applicationInfo.isEAP,
        embeddedFrontendEnabled = context.isEmbeddedFrontendEnabled,
      )
    }
    val macAppIconSources = if (!hasMacApp) {
      emptyMap()
    }
    else {
      val release = devMacAppIcons(macCustomizer, context.productProperties, isEap = false)
      val eap = devMacAppIcons(macCustomizer, context.productProperties, isEap = true)
      release.mapValues { (name, path) -> path to eap.getValue(name) }
    }
    return SplitPlatformResources(
      model = model,
      files = files,
      coreClassPathJarNames = coreClassPathJarNames,
      macBundleFiles = macBundleFiles,
      macAppIcons = macAppIcons,
      macAppIconSources = macAppIconSources,
    )
  }
}

private fun createConfiguration(homePath: Path): ProductConfigurationRegistry {
  val projectPropertiesPath = getProductPropertiesPath(homePath)
  return Json.decodeFromString(Files.readString(projectPropertiesPath))
}

internal fun getProductPropertiesPath(homePath: Path): Path {
  // handle a custom product properties path
  return System.getProperty(CUSTOM_PRODUCT_PROPERTIES_PATH)?.let { homePath.resolve(it) }?.takeIf { Files.exists(it) }
         ?: homePath.resolve(PRODUCT_REGISTRY_PATH)
}

/** Creates the `ProductProperties` that a dev build of [platformPrefix] uses. */
@ApiStatus.Internal
fun createProductProperties(projectDir: Path, platformPrefix: String, outputProvider: ModuleOutputProvider): ProductProperties {
  return createProductProperties(
    productConfiguration = getProductConfiguration(createConfiguration(projectDir), platformPrefix, baseIdePlatformPrefixForFrontend = null),
    outputProvider = outputProvider,
    projectDir = projectDir,
    platformPrefix = platformPrefix,
  )
}

private fun getProductConfiguration(configuration: ProductConfigurationRegistry, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): ProductConfiguration {
  val key = if (baseIdePlatformPrefixForFrontend == null) platformPrefix else "$baseIdePlatformPrefixForFrontend$platformPrefix"
  return configuration.products.get(key)
         ?: throw ConfigurationException("No production configuration for `$key`; please add to `${PRODUCT_REGISTRY_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)
