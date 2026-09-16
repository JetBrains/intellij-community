package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.DistFileRow
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProductModuleIdentity
import com.intellij.platform.distributionContent.ProductModuleSelection
import com.intellij.platform.distributionContent.ProductModuleSetReference
import com.intellij.platform.distributionContent.ProductPlatformReview
import com.intellij.platform.distributionContent.ProductPublishingReview
import com.intellij.platform.distributionContent.ProductReviewReport
import com.intellij.platform.distributionContent.ProductXmlReference
import com.intellij.platform.pluginSystem.parser.impl.elements.xmlValue
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ProductProperties

/** Combines declared product choices with the plugins and distribution files observed during packaging. */
@Internal
fun deriveProductReviewReport(
  productProperties: ProductProperties,
  bundledPlugins: List<PluginContentReport>,
  nonBundledPlugins: List<PluginContentReport>,
  distFiles: List<DistFileRow>,
  platformEntries: List<FileEntry>,
): ProductReviewReport {
  val layout = productProperties.productLayout
  val spec = productProperties.getProductContentDescriptor()
  val platform = if (spec == null) {
    val plugins = platformEntries.single { it.name == "plugins" }
    ProductPlatformReview(
      legacyProductModules = plugins.productModules,
      legacyProductEmbeddedModules = plugins.productEmbeddedModules,
    )
  }
  else {
    ProductPlatformReview(
      moduleSets = spec.moduleSets.map { reference ->
        ProductModuleSetReference(
          name = reference.moduleSet.name,
          loadingOverrides = reference.loadingOverrides.entries.associate { it.key.value to it.value.xmlValue },
        )
      },
      modules = spec.additionalModules.map { module ->
        ProductModuleSelection(
          name = module.moduleId.name,
          namespace = module.moduleId.namespace,
          loading = module.loading.xmlValue,
          requiredIfAvailable = module.requiredIfAvailable?.let { ProductModuleIdentity(it.name, it.namespace) },
          includeDependencies = module.includeDependencies,
        )
      },
      xmlIncludes = spec.deprecatedXmlIncludes.map { reference ->
        ProductXmlReference(reference.contentModuleName.value, reference.resourcePath, reference.optional)
      },
    )
  }
  return ProductReviewReport(
    bundledPlugins = bundledPlugins,
    nonBundledPlugins = nonBundledPlugins,
    publishing = ProductPublishingReview(
      buildAllCompatiblePlugins = layout.buildAllCompatiblePlugins,
      pluginModulesToPublish = layout.pluginModulesToPublish.toList(),
      compatiblePluginsToIgnore = layout.compatiblePluginsToIgnore.toList(),
    ),
    platform = platform,
    distFiles = distFiles,
  )
}
