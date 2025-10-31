// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.resolveAndEmbedContentModuleDescriptor
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.isOptionalLoadingRule
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.jetbrains.jps.model.java.JavaSourceRootType

// Contrary to what it looks like, this is not a step back.
// Previously, it was specified in PLATFORM_IMPLEMENTATION_MODULES/PLATFORM_API_MODULES.
// Once the shape of the extracted module becomes fully discernible,
// we can consider ways to improve `pluginAuto` and eliminate the need for an explicit declaration here.
internal val PRODUCT_MODULE_IMPL_COMPOSITION = java.util.Map.of(
  "intellij.rider", listOf(
  "intellij.platform.debugger.modulesView"
),
  "intellij.platform.rpc.backend", listOf(
  "fleet.rpc.server",
)
)

internal fun getProductModuleJarName(moduleName: String, context: BuildContext, frontendModuleFilter: FrontendModuleFilter): String {
  return when {
    isModuleCloseSource(moduleName = moduleName, context = context) -> if (frontendModuleFilter.isBackendModule(moduleName)) PRODUCT_BACKEND_JAR else PRODUCT_JAR
    else -> PlatformJarNames.getPlatformModuleJarName(moduleName, frontendModuleFilter)
  }
}

// result _must be_ consistent, do not use Set.of or HashSet here
internal suspend fun processAndGetProductPluginContentModules(
  layout: PlatformLayout,
  descriptorCache: ScopedCachedDescriptorContainer,
  includedPlatformModulesPartialList: Collection<String>,
  context: BuildContext,
): Set<ModuleItem> {
  return withContext(Dispatchers.IO) {
    val productPluginSourceModuleName = context.productProperties.applicationInfoModule
    val file = requireNotNull(
      context.findFileInModuleSources(productPluginSourceModuleName, PLUGIN_XML_RELATIVE_PATH)
      ?: context.findFileInModuleSources(moduleName = productPluginSourceModuleName, relativePath = "META-INF/${context.productProperties.platformPrefix}Plugin.xml")
    ) { "Cannot find product plugin descriptor in '$productPluginSourceModuleName' module" }

    val element: Element
    val moduleToSetChainMapping: Map<String, List<String>>?
    val programmaticModulesSpec = context.productProperties.getProductContentDescriptor()
    if (programmaticModulesSpec == null) {
      element = JDOMUtil.load(file)
      moduleToSetChainMapping = null
    }
    else {
      val buildResult = buildProductContentXml(
        spec = programmaticModulesSpec,
        moduleOutputProvider = context,
        inlineXmlIncludes = true,
        inlineModuleSets = true,
        productPropertiesClass = context.productProperties::class.java.name,
        generatorCommand = "(runtime)",
        isUltimateBuild = context.paths.projectHome != context.paths.communityHomeDir
      )
      Span.current().addEvent("Generated ${buildResult.contentBlocks.size} content blocks with ${buildResult.contentBlocks.sumOf { it.modules.size }} total modules")

      element = JDOMUtil.load(buildResult.xml)
      moduleToSetChainMapping = buildResult.moduleToSetChainMapping
    }

    // Scrambling isn’t an issue: the scrambler can modify XML.
    // If a file is included, we assume—and it should be the case—that both the including module and the module containing the included file are scrambled together.
    // Note: CDATA isn’t processed, so embedded content modules use different logic.
    // We must resolve includes to collect all content modules, since the <content> tag may
    // be specified in an included file. This is done not only for performance but for correctness.
    val xIncludeResolver = XIncludeElementResolverImpl(
      searchPath = listOf(DescriptorSearchScope(includedPlatformModulesPartialList, descriptorCache)),
      context = context
    )
    resolveIncludes(element = element, elementResolver = xIncludeResolver)

    val frontendModuleFilter = context.getFrontendModuleFilter()
    val moduleItems = LinkedHashSet<ModuleItem>()
    filterAndProcessContentModules(rootElement = element, pluginMainModuleName = null, context = context) { moduleElement, moduleName, loadingRule ->
      processProductModule(
        isEmbedded = loadingRule != null && loadingRule == ModuleLoadingRule.EMBEDDED.name.lowercase(),
        moduleName = moduleName,
        moduleElement = moduleElement,
        frontendModuleFilter = frontendModuleFilter,
        result = moduleItems,
        moduleToSetChainOverride = moduleToSetChainMapping,
        descriptorCache = descriptorCache,
        xIncludeResolver = xIncludeResolver,
        context = context,
      )
    }

    val data = JDOMUtil.write(element)
    layout.withPatch { moduleOutputPatcher, _, _ ->
      moduleOutputPatcher.patchModuleOutput(moduleName = productPluginSourceModuleName, path = "META-INF/${file.fileName}", content = data)
    }

    descriptorCache.put(PRODUCT_DESCRIPTOR_META_PATH, data.encodeToByteArray())

    moduleItems
  }
}

internal suspend inline fun filterAndProcessContentModules(
  rootElement: Element,
  pluginMainModuleName: String?,
  context: BuildContext,
  crossinline contentHandler: suspend (moduleElement: Element, moduleName: String, loadingRule: String?) -> Unit,
) {
  var contentModuleFilter: ContentModuleFilter? = null
  for (content in rootElement.getChildren("content")) {
    val iterator = content.getChildren("module").iterator()
    while (iterator.hasNext()) {
      val moduleElement = iterator.next()
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) {
        "Module name is not specified for ${JDOMUtil.writeElement(moduleElement)}"
      }
      val loadingRule = moduleElement.getAttributeValue("loading")
      if (isOptionalLoadingRule(loadingRule)) {
        if (contentModuleFilter == null) {
          contentModuleFilter = context.getContentModuleFilter()
        }

        if (!contentModuleFilter.isOptionalModuleIncluded(moduleName = moduleName.substringBeforeLast('/'), pluginMainModuleName = pluginMainModuleName)) {
          Span.current().addEvent("Module '$moduleName' is excluded from ${if (pluginMainModuleName == null) "product" else "plugin $pluginMainModuleName"} by $contentModuleFilter")
          iterator.remove()
          continue
        }
      }

      contentHandler(moduleElement, moduleName, loadingRule)
    }
  }
}

private suspend fun processProductModule(
  moduleElement: Element,
  frontendModuleFilter: FrontendModuleFilter,
  result: LinkedHashSet<ModuleItem>,
  moduleToSetChainOverride: Map<String, List<String>>? = null,
  descriptorCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  context: BuildContext,
  moduleName: String,
  isEmbedded: Boolean,
) {
  val isInScrambledFile = isEmbedded && isModuleCloseSource(moduleName = moduleName, context = context)
  val relativeOutFile = if (isInScrambledFile) {
    if (frontendModuleFilter.isBackendModule(moduleName)) PRODUCT_BACKEND_JAR else PRODUCT_JAR
  }
  else {
    "$moduleName.jar"
  }

  // extract module set from override mapping (for programmatic spec)
  val moduleSet = moduleToSetChainOverride?.get(moduleName)
  result.add(
    ModuleItem(
      moduleName = moduleName,
      relativeOutputFile = relativeOutFile,
      reason = if (isEmbedded) ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES else ModuleIncludeReasons.PRODUCT_MODULES,
      moduleSet = moduleSet,
    )
  )
  PRODUCT_MODULE_IMPL_COMPOSITION.get(moduleName)?.let { list ->
    list
      .filter { !context.productProperties.productLayout.productImplementationModules.contains(it) }
      .mapTo(result) { subModuleName ->
        ModuleItem(moduleName = subModuleName, relativeOutputFile = relativeOutFile, reason = ModuleIncludeReasons.PRODUCT_MODULES, moduleSet = moduleSet)
      }
  }

  // We do not embed the module descriptor because scrambling can rename classes.
  //
  // However, we cannot rely solely on the `PLUGIN_CLASSPATH` descriptor: for non-embedded modules,
  // xi:included files (e.g., META-INF/VcsExtensionPoints.xml) are not resolvable from the core classpath, since a non-embedded module uses a separate classloader.
  //
  // Because scrambling applies only (by policy) to embedded modules, we embed the module descriptor for non-embedded modules to address this.
  //
  // Note: We could implement runtime loading via the module's classloader, but that would significantly complicate the runtime code.
  if (!isInScrambledFile) {
    resolveAndEmbedContentModuleDescriptor(
      moduleElement = moduleElement,
      descriptorCache = descriptorCache,
      xIncludeResolver = xIncludeResolver,
      context = context,
    )
  }
  // For Gateway or a module-based loader, where PLUGIN_CLASSPATH isn’t used, performance will be slightly affected
  // (most product modules shouldn’t be embedded anyway).
  // That’s acceptable because remote development will be migrated to the path-based class loader anyway.
  // We prefer not to increase code complexity without a strong reason.
}

private fun isModuleCloseSource(moduleName: String, context: BuildContext): Boolean {
  if (moduleName.endsWith(".resources") || moduleName.endsWith(".icons") || moduleName.startsWith(LIB_MODULE_PREFIX)) {
    return false
  }

  val sourceRoots = context.findRequiredModule(moduleName).sourceRoots.filter { it.rootType == JavaSourceRootType.SOURCE }
  if (sourceRoots.isEmpty()) {
    return false
  }

  return sourceRoots.any {
    !it.path.startsWith(context.paths.communityHomeDir)
  }
}

internal fun contentModuleNameToDescriptorFileName(moduleName: String): String = "${moduleName.replace('/', '.')}.xml"