// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.productInfo.ProductLaunchModel
import org.jetbrains.intellij.build.impl.productInfo.computeDevProductLaunchModel
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
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
 */
@ApiStatus.Internal
class SplitPlatformResources(
  @JvmField val model: ProductLaunchModel,
  @JvmField val files: Map<Pair<OsFamily, JvmArchitecture>, Map<String, String>>,
)

/**
 * Renders the files of the `platform_resources` component of [platformPrefix] for each of [platforms] with the
 * production writers.
 *
 * The files come from `writePlatformResourceFiles` over a build context with no additional modules, no runtime module
 * repository, and the pinned [buildDateInSeconds]. The launch model tests compare them with the renderers of [model].
 * [scratchDir] must be empty.
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
    fragment = DevBuildFragment(name = "platform_resources", platform = null, runtimeModuleRepository = false),
  )
  BuildLifetime().use { lifetime ->
    val context = createBuildContextFromProject(
      productConfiguration = productConfiguration,
      request = request,
      buildDir = Files.createDirectories(scratchDir.resolve("run")),
      buildOptionsTemplate = BuildOptions(),
      lifetime = lifetime,
    )
    val files = platforms.associateWith { (os, arch) ->
      configureTargetPlatform(context.options, request.copy(os = os, arch = arch))
      val runDir = Files.createDirectories(scratchDir.resolve("${os.osId}-${arch.name}"))
      writePlatformResourceFiles(context, os, arch, runDir).associate { runDir.relativize(it).invariantSeparatorsPathString to Files.readString(it) }
    }
    return SplitPlatformResources(
      model = computeDevProductLaunchModel(context.productProperties, context.outputProvider, buildDateInSeconds),
      files = files,
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

private fun getProductConfiguration(configuration: ProductConfigurationRegistry, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): ProductConfiguration {
  val key = if (baseIdePlatformPrefixForFrontend == null) platformPrefix else "$baseIdePlatformPrefixForFrontend$platformPrefix"
  return configuration.products.get(key)
         ?: throw ConfigurationException("No production configuration for `$key`; please add to `${PRODUCT_REGISTRY_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)
