package com.intellij.platform.distributionContent

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

/** Product choices for review in Git. Expanded observations belong in test artifacts. */
@Internal
data class ProductReviewReport(
  @JvmField val bundledPlugins: List<PluginContentReport>,
  @JvmField val nonBundledPlugins: List<PluginContentReport>,
  @JvmField val publishing: ProductPublishingReview,
  @JvmField val platform: ProductPlatformReview,
  @JvmField val distFiles: List<DistFileRow>,
)

@Internal
@Serializable
data class ProductPublishingReview(
  @JvmField val buildAllCompatiblePlugins: Boolean,
  @JvmField val pluginModulesToPublish: List<String>,
  @JvmField val compatiblePluginsToIgnore: List<String>,
)

@Internal
@Serializable
data class ProductPlatformReview(
  @JvmField val moduleSets: List<ProductModuleSetReference> = emptyList(),
  @JvmField val modules: List<ProductModuleSelection> = emptyList(),
  @JvmField val xmlIncludes: List<ProductXmlReference> = emptyList(),
  @JvmField val legacyProductModules: List<String> = emptyList(),
  @JvmField val legacyProductEmbeddedModules: List<String> = emptyList(),
)

@Internal
@Serializable
data class ProductModuleSetReference(
  @JvmField val name: String,
  @JvmField val loadingOverrides: Map<String, String> = emptyMap(),
)

@Internal
@Serializable
data class ProductModuleIdentity(
  @JvmField val name: String,
  @JvmField val namespace: String? = "jetbrains",
)

@Internal
@Serializable
data class ProductModuleSelection(
  @JvmField val name: String,
  @JvmField val namespace: String? = "jetbrains",
  @JvmField val loading: String = "optional",
  @JvmField val requiredIfAvailable: ProductModuleIdentity? = null,
  @JvmField val includeDependencies: Boolean = false,
)

@Internal
@Serializable
data class ProductXmlReference(
  @JvmField val module: String,
  @JvmField val resourcePath: String,
  @JvmField val optional: Boolean = false,
)

@Serializable
private class ProductReviewYaml(
  val bundledPlugins: List<PluginContentReport>,
  val nonBundledPlugins: List<PluginContentReport>,
  val publishing: ProductPublishingReview,
  val platform: ProductPlatformReview,
  val distFiles: List<DistFileGroup>,
)

/** Reads the product choices and grouped distribution files. */
@Internal
fun readProductReviewReport(text: String): ProductReviewReport {
  val report = yaml.decodeFromString(ProductReviewYaml.serializer(), text)
  return ProductReviewReport(report.bundledPlugins, report.nonBundledPlugins, report.publishing, report.platform, readDistFileGroups(report.distFiles))
}

/** Writes product choices in canonical order without expanding shared module sets. */
@Internal
fun writeProductReviewReport(report: ProductReviewReport): String {
  val platform = report.platform
  return yaml.encodeToString(ProductReviewYaml.serializer(), ProductReviewYaml(
    bundledPlugins = summarizePlugins(report.bundledPlugins),
    nonBundledPlugins = summarizePlugins(report.nonBundledPlugins),
    publishing = report.publishing.copy(
      pluginModulesToPublish = report.publishing.pluginModulesToPublish.distinct().sorted(),
      compatiblePluginsToIgnore = report.publishing.compatiblePluginsToIgnore.distinct().sorted(),
    ),
    platform = platform.copy(
      moduleSets = platform.moduleSets.map { it.copy(loadingOverrides = it.loadingOverrides.toSortedMap()) }
        .distinct().sortedWith(compareBy({ it.name }, { yaml.encodeToString(ProductModuleSetReference.serializer(), it) })),
      modules = platform.modules.distinct()
        .sortedWith(compareBy({ it.name }, { it.namespace }, { yaml.encodeToString(ProductModuleSelection.serializer(), it) })),
      xmlIncludes = platform.xmlIncludes.distinct().sortedWith(compareBy({ it.module }, { it.resourcePath }, { it.optional })),
      legacyProductModules = platform.legacyProductModules.distinct().sorted(),
      legacyProductEmbeddedModules = platform.legacyProductEmbeddedModules.distinct().sorted(),
    ),
    distFiles = groupDistFiles(report.distFiles),
  )).trimEnd() + "\n"
}
