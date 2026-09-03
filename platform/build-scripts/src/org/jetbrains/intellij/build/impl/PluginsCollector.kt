// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.descriptorResolveContext
import org.jetbrains.intellij.build.classPath.resolveIncludes
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use

private const val CORE_PLUGIN_ID = "com.intellij"

suspend fun collectCompatiblePluginsToPublish(pluginsToPublish: MutableSet<PluginLayout>, platformLayout: PlatformLayout, context: BuildContext) {
  val availableModulesAndPlugins = HashSet(collectBundledLayoutNames(platformLayout = platformLayout, context = context))

  val minimal = System.getProperty("intellij.build.minimal").toBoolean()
  // One walk over the project serves both maps. The walk reads every plugin descriptor once, and the two maps
  // differ only in the bundled plugins and in the implementation-detail plugins, which a filter states.
  val allDescriptors = collectPluginDescriptors(skipImplementationDetails = false, skipBundled = false, honorCompatiblePluginsToIgnore = true, context = context)
  val bundledPluginModules = java.util.Set.copyOf(context.getBundledPluginModules())
  val descriptorMap = allDescriptors.filterValuesTo { it.mainModule !in bundledPluginModules && (minimal || !it.isImplementationDetail) }
  val descriptorMapWithBundled = allDescriptors.filterValuesTo { !it.isImplementationDetail }
  val productModuleAliases = context.productProperties.getProductContentDescriptor()?.productModuleAliases?.map { it.value } ?: emptyList()
  val bundledPluginIds = descriptorMapWithBundled.values
    .asSequence().map { it.id }
    .plus(productModuleAliases)
    .minus(descriptorMap.values.asSequence().map { it.id }.toSet())
    .toSet()
  for (descriptor in descriptorMap.values) {
    if (isPluginCompatible(
        plugin = descriptor,
        availableModulesAndPlugins = availableModulesAndPlugins,
        nonCheckedModules = descriptorMapWithBundled,
        bundledPluginIds = bundledPluginIds,
      )) {
      val layouts = descriptor.pluginLayouts.toMutableList()
      if (layouts.size == 2 && layouts.get(0).bundlingRestrictions != layouts.get(1).bundlingRestrictions) {
        layouts.retainAll { it.bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE }
      }
      layouts.retainAll { it.bundlingRestrictions.includeInDistribution != PluginDistribution.CROSS_PLATFORM_DIST_ONLY }
      pluginsToPublish.addAll(layouts)
      if (layouts.size > 1) {
        Span.current().addEvent("Module '${descriptor.mainModule}' have ${layouts.size} layouts: $layouts")
      }
    }
  }
}

/**
 * Returns the names that the product reports as its own layout.
 *
 * The set holds the core plugin id, the plugin aliases and the content module names of the product descriptor,
 * the plugin aliases of each product content module, and the id, the plugin aliases and the content module names
 * of each bundled plugin.
 *
 * The build reads every name from a declaration. It does not start the headless IDE for this answer any more.
 * The answer is now independent of the host OS. The IDE dropped each plugin that needs another OS.
 */
suspend fun collectBundledLayoutNames(platformLayout: PlatformLayout, context: BuildContext): Set<String> {
  return spanBuilder("collect bundled layout names").use { span ->
    val result = LinkedHashSet<String>()
    result.add(CORE_PLUGIN_ID)

    val productDescriptorData = requireNotNull(platformLayout.descriptorCacheContainer.forPlatform(platformLayout).getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH)) {
      "The platform layout holds no product descriptor under '$PRODUCT_DESCRIPTOR_META_PATH'"
    }
    val productDescriptor = JDOMUtil.load(productDescriptorData.decodeToString())
    addPluginAliases(productDescriptor, result)
    for (content in productDescriptor.getChildren("content")) {
      for (module in content.getChildren("module")) {
        val contentModuleName = module.getAttributeValue("name")
        if (contentModuleName.isNullOrEmpty()) {
          continue
        }

        addContentModuleAliases(contentModuleName = contentModuleName, result = result, context = context)
        result.add(contentModuleName)
      }
    }

    // the DSL declares an alias that the generated product descriptor can omit
    val productModuleAliases = context.productProperties.getProductContentDescriptor()?.productModuleAliases
    if (productModuleAliases != null) {
      for (alias in productModuleAliases) {
        result.add(alias.value)
      }
    }

    val bundledPluginModules = context.getBundledPluginModules()
    val allBundledPlugins = java.util.Set.copyOf(bundledPluginModules)
    val nonTrivialPlugins = groupPluginLayoutsByMainModule(context.productProperties.productLayout)
    for (moduleName in bundledPluginModules) {
      // the runtime enables an implementation-detail plugin too, so this walk does not skip one
      val descriptor = readPluginDescriptor(
        moduleName = moduleName,
        skipImplementationDetails = false,
        applyPublishFilters = false,
        allBundledPlugins = allBundledPlugins,
        nonTrivialPlugins = nonTrivialPlugins,
        context = context,
      ) ?: continue

      result.add(descriptor.id)
      result.addAll(descriptor.declaredModules)
    }

    span.setAttribute("count", result.size.toLong())
    result
  }
}

internal fun isPluginCompatible(
  plugin: PluginDescriptor,
  availableModulesAndPlugins: MutableSet<String>,
  nonCheckedModules: MutableMap<String, PluginDescriptor>,
  bundledPluginIds: Set<String>,
): Boolean {
  val includedModules = plugin.pluginLayouts.asSequence()
    .flatMap { it.includedModules.asSequence() }
    .mapTo(HashSet()) { it.moduleName }
  nonCheckedModules.remove(plugin.id)
  for (declaredModule in plugin.declaredModules) {
    nonCheckedModules.remove(declaredModule)
  }
  for (requiredDependency in plugin.requiredDependencies) {
    if (availableModulesAndPlugins.contains(requiredDependency)
        || includedModules.contains(requiredDependency)
        || requiredDependency.startsWith("com.intellij.modules.os.")
        || requiredDependency.startsWith("com.intellij.modules.arch.")) {
      continue
    }

    val requiredPlugin = nonCheckedModules[requiredDependency]
    if (requiredPlugin != null && isPluginCompatible(requiredPlugin, availableModulesAndPlugins, nonCheckedModules, bundledPluginIds)) {
      continue
    }

    Span.current().addEvent("${plugin.id} is not compatible because no required dependency is available: $requiredDependency")
    return false
  }
  for (incompatiblePlugin in plugin.incompatiblePlugins) {
    if (bundledPluginIds.contains(incompatiblePlugin)) {
      Span.current().addEvent("${plugin.id} is not compatible because it is incompatible with a bundled plugin: $incompatiblePlugin")
      return false
    }
  }
  availableModulesAndPlugins.add(plugin.id)
  availableModulesAndPlugins.addAll(plugin.declaredModules)
  return true
}

suspend fun collectPluginDescriptors(
  skipImplementationDetails: Boolean,
  skipBundled: Boolean,
  honorCompatiblePluginsToIgnore: Boolean,
  context: BuildContext,
): MutableMap<String, PluginDescriptor> {
  return spanBuilder("collect plugin descriptors")
    .setAttribute("skip.implementation.details", skipImplementationDetails)
    .setAttribute("skip.bundled", skipBundled)
    .setAttribute("honor.compatible.plugins.to.ignore", honorCompatiblePluginsToIgnore)
    .use {
      val productLayout = context.productProperties.productLayout
      val nonTrivialPlugins = groupPluginLayoutsByMainModule(productLayout)
      val allBundledPlugins = java.util.Set.copyOf(context.getBundledPluginModules())

      val candidates = context.project.modules.filter { jpsModule ->
        val moduleName = jpsModule.name
        !(skipBundled && allBundledPlugins.contains(moduleName)) &&
        !(honorCompatiblePluginsToIgnore && productLayout.compatiblePluginsToIgnore.contains(moduleName))
      }
      // Each read parses one descriptor and resolves its includes, so the reads run in parallel. The map keeps the
      // project order, because a later duplicate key must win the same way it did in a sequential loop.
      val descriptors = candidates.mapConcurrent { jpsModule ->
        readPluginDescriptor(
          moduleName = jpsModule.name,
          skipImplementationDetails = skipImplementationDetails,
          applyPublishFilters = true,
          allBundledPlugins = allBundledPlugins,
          nonTrivialPlugins = nonTrivialPlugins,
          context = context,
        )
      }

      val pluginDescriptors = LinkedHashMap<String, PluginDescriptor>()
      for (pluginDescriptor in descriptors) {
        if (pluginDescriptor == null) {
          continue
        }
        pluginDescriptors.put(pluginDescriptor.id, pluginDescriptor)
        for (module in pluginDescriptor.declaredModules) {
          pluginDescriptors.put(module, pluginDescriptor)
        }
      }
      pluginDescriptors
    }
}

/** The entries whose descriptor passes [predicate], in the order of this map. Every key of a kept descriptor stays. */
private fun Map<String, PluginDescriptor>.filterValuesTo(predicate: (PluginDescriptor) -> Boolean): MutableMap<String, PluginDescriptor> {
  val result = LinkedHashMap<String, PluginDescriptor>()
  for ((key, descriptor) in this) {
    if (predicate(descriptor)) {
      result.put(key, descriptor)
    }
  }
  return result
}

private fun groupPluginLayoutsByMainModule(productLayout: ProductModulesLayout): Map<String, List<PluginLayout>> {
  val result = HashMap<String, MutableList<PluginLayout>>(productLayout.pluginLayouts.size)
  for (pluginLayout in productLayout.pluginLayouts) {
    result.getOrPut(pluginLayout.mainModule) { mutableListOf() }.add(pluginLayout)
  }
  return result
}

/**
 * Reads the plugin descriptor of one JPS module and resolves its xi:includes.
 *
 * Returns null when the module declares no plugin, or when the descriptor is not a plugin the build can publish.
 * Each such case adds a "skip module" event to the current span.
 *
 * [applyPublishFilters] turns on the filters that find a module which the build must not publish as a plugin.
 * A walk over the bundled plugins sets it to false, because the list of the bundled plugins is the authority.
 */
private suspend fun readPluginDescriptor(
  moduleName: String,
  skipImplementationDetails: Boolean,
  applyPublishFilters: Boolean,
  allBundledPlugins: Set<String>,
  nonTrivialPlugins: Map<String, List<PluginLayout>>,
  context: BuildContext,
): PluginDescriptor? {
  // when we migrate to Bazel, we will use a test marker to avoid checking the module name for "test" pattern
  if (moduleName.contains(".tests.") && !allBundledPlugins.contains(moduleName)) {
    return null
  }

  // not a plugin
  if (context.productProperties.platformPrefix != "FleetBackend" && moduleName.startsWith("fleet.plugins.")) {
    return null
  }

  val outputProvider = context.outputProvider
  val pluginXml = findFileInModuleSources(module = outputProvider.findRequiredModule(moduleName), relativePath = "META-INF/plugin.xml", onlyProductionSources = true) ?: return null

  val xml = JDOMUtil.load(pluginXml)
  check(!xml.isEmpty) {
    "Module '$moduleName': '$pluginXml' is empty"
  }

  if (applyPublishFilters &&
      (xml.getChildTextTrim("id") == CORE_PLUGIN_ID || hasPluginAliasThatIndicatesThatItIsAProduct(xml))) {
    Span.current().addEvent(
      "skip module",
      Attributes.of(
        AttributeKey.stringKey("name"), moduleName,
        AttributeKey.stringKey("reason"), "product descriptor",
        AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
      ),
    )
    return null
  }

  val isImplementationDetail = xml.getAttributeValue("implementation-detail") == "true"
  if (skipImplementationDetails && isImplementationDetail) {
    Span.current().addEvent(
      "skip module",
      Attributes.of(
        AttributeKey.stringKey("name"), moduleName,
        AttributeKey.stringKey("reason"), "'implementation-detail' == 'true'",
        AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
      )
    )
    return null
  }

  // a non-product plugin cannot include VCS and other such platform modules in the content
  if (applyPublishFilters && xml.getChildren("content").any { contentElement ->
      contentElement.getChildren("module").any {
        val contentModuleName = it.getAttributeValue("name", "")
        //intellij.platform.vcs.*.split modules are currently included in the CodeWithMe plugin
        contentModuleName.startsWith("intellij.platform.vcs.") && !contentModuleName.endsWith(".split") || contentModuleName == "intellij.ide.startup.importSettings"
      }
    }) {
    Span.current().addEvent(
      "skip module",
      Attributes.of(
        AttributeKey.stringKey("name"), moduleName,
        AttributeKey.stringKey("reason"), "product descriptor",
        AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
      ),
    )
    return null
  }

  val pluginLayouts = nonTrivialPlugins.get(moduleName) ?: listOf(PluginLayout.pluginAuto(listOf(moduleName)))
  val descriptorCacheContainer = DescriptorCacheContainer()
  resolveIncludes(
    element = xml,
    elementResolver = XIncludeElementResolverImpl(
      searchPath = listOf(
        DescriptorSearchScope(
          modules = pluginLayouts.flatMap { it.includedModules }.mapTo(LinkedHashSet()) { it.moduleName },
          descriptorCache = descriptorCacheContainer.forPlugin(pluginXml),
          searchInDependencies = DescriptorSearchScope.SearchMode.PLUGIN_COLLECTOR,
        ),
      ),
      context = descriptorResolveContext(context),
    )
  )

  val id = xml.getChildTextTrim("id") ?: xml.getChildTextTrim("name")
  if (id.isNullOrEmpty()) {
    Span.current().addEvent(
      "skip module", Attributes.of(
        AttributeKey.stringKey("name"), moduleName,
        AttributeKey.stringKey("reason"), "does not contain <id/> element",
        AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
      )
    )
    return null
  }

  if (applyPublishFilters) {
    if (id == "com.intellij.modules.ultimate" && !allBundledPlugins.contains(id)) {
      // if the 'ultimate' module is not mentioned in the list of bundled plugins,
      // then this module does not exist in a form of plugin in this distribution and should be ignored
      return null
    }

    // Even though Database plugin does not depend on Ultimate anymore,
    // we do not include it in the Community IDEs
    if (id == "com.intellij.database" && !allBundledPlugins.contains("com.intellij.modules.ultimate")) {
      return null
    }
  }

  val declaredModules = HashSet<String>()
  addPluginAliases(xml, declaredModules)
  // one `<content>` block per namespace, so every block counts
  for (content in xml.getChildren("content")) {
    for (module in content.getChildren("module")) {
      val contentModuleName = module.getAttributeValue("name")
      if (contentModuleName != null && !contentModuleName.isEmpty()) {
        addContentModuleAliases(contentModuleName = contentModuleName, result = declaredModules, context = context)
        declaredModules.add(contentModuleName)
      }
    }
  }

  val requiredDependencies = HashSet<String>()
  val optionalDependencies = ArrayList<Pair<String, String>>()
  for (dependency in xml.getChildren("depends")) {
    if (dependency.getAttributeValue("optional") != "true") {
      requiredDependencies.add(dependency.textTrim)
    }
    else {
      optionalDependencies.add(Pair(dependency.textTrim, dependency.getAttributeValue("config-file")))
    }
  }
  val dependencies = xml.getChild("dependencies")
  if (dependencies != null) {
    for (plugin in dependencies.getChildren("plugin")) {
      val pluginId = plugin.getAttributeValue("id")
      if (pluginId != null) {
        requiredDependencies.add(pluginId)
      }
    }
    for (module in dependencies.getChildren("module")) {
      val name = module.getAttributeValue("name")
      if (name != null && !name.isEmpty()) {
        requiredDependencies.add(name)
      }
    }
  }

  val incompatiblePlugins = HashSet<String>()
  for (pluginId in xml.getChildren("incompatible-with")) {
    incompatiblePlugins.add(pluginId.textTrim)
  }

  return PluginDescriptor(
    id = id,
    description = xml.getChildTextTrim("description"),
    declaredModules = declaredModules,
    requiredDependencies = requiredDependencies,
    incompatiblePlugins = incompatiblePlugins,
    optionalDependencies = optionalDependencies,
    mainModule = moduleName,
    pluginLayouts = pluginLayouts,
    isImplementationDetail = isImplementationDetail,
  )
}

private fun addPluginAliases(element: Element, result: MutableSet<String>) {
  for (moduleElement in element.getChildren("module")) {
    val value = moduleElement.getAttributeValue("value")
    if (value != null) {
      result.add(value)
    }
  }
}

/**
 * Adds the plugin aliases that the descriptor of one content module declares.
 * The descriptor file is in the production sources of the JPS module that owns the content module.
 */
private fun addContentModuleAliases(contentModuleName: String, result: MutableSet<String>, context: BuildContext) {
  val jpsModuleName = contentModuleName.substringBeforeLast('/')
  val jpsContentModule = context.outputProvider.findModule(jpsModuleName) ?: return
  val moduleFile = findFileInModuleSources(
    module = jpsContentModule,
    relativePath = contentModuleNameToDescriptorFileName(contentModuleName),
    onlyProductionSources = true,
  ) ?: return
  addPluginAliases(JDOMUtil.load(moduleFile), result)
}

private fun hasPluginAliasThatIndicatesThatItIsAProduct(xml: Element): Boolean {
  return xml.getChildren("module").any {
    val alias = it.getAttributeValue("value")
    alias == "com.intellij.marketplace" || alias == "com.jetbrains.gateway"
  }
}

class PluginDescriptor(
  @JvmField val id: String,
  @JvmField val description: String?,
  @JvmField val declaredModules: Set<String>,
  @JvmField val requiredDependencies: Set<String>,
  @JvmField val incompatiblePlugins: Set<String>,
  @JvmField val optionalDependencies: List<Pair<String, String>>,
  @JvmField val mainModule: String,
  @JvmField val pluginLayouts: List<PluginLayout>,
  /** Whether the descriptor states `implementation-detail="true"`. A user cannot disable such a plugin. */
  @JvmField val isImplementationDetail: Boolean = false,
)
