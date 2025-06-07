// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import java.nio.file.Path

suspend fun collectCompatiblePluginsToPublish(builtinModuleData: BuiltinModulesFileData, pluginsToPublish: MutableSet<PluginLayout>, context: BuildContext) {
  val availableModulesAndPlugins = HashSet<String>(builtinModuleData.layout.size)
  builtinModuleData.layout.mapTo(availableModulesAndPlugins) { it.name }

  val minimal = System.getProperty("intellij.build.minimal").toBoolean()
  val descriptorMap = collectPluginDescriptors(skipImplementationDetails = !minimal, skipBundled = true, honorCompatiblePluginsToIgnore = true, context)
  val descriptorMapWithBundled = collectPluginDescriptors(skipImplementationDetails = true, skipBundled = false, honorCompatiblePluginsToIgnore = true, context)

  // While collecting PluginDescriptor maps above, we may have chosen incorrect PluginLayout.
  // Let's check that and substitute an incorrectly chosen one with a more suitable one or report an error.
  val moreThanOneLayoutMap = context.productProperties.productLayout.pluginLayouts.groupBy { it.mainModule }.filterValues { it.size > 1 }
  val moreThanOneLayoutSubstitutors = HashMap<PluginLayout, PluginLayout>()
  for ((module, layouts) in moreThanOneLayoutMap) {
    Span.current().addEvent("Module '$module' have ${layouts.size} layouts: $layouts")
    val substitutor = layouts.firstOrNull { it.bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE }
                      ?: layouts.firstOrNull { it.bundlingRestrictions == PluginBundlingRestrictions.NONE }
                      ?: continue
    for (layout in layouts) {
      if (layout != substitutor) {
        moreThanOneLayoutSubstitutors.put(layout, substitutor)
      }
    }
  }

  val errors = ArrayList<List<PluginLayout>>()
  for (descriptor in descriptorMap.values) {
    if (isPluginCompatible(descriptor, availableModulesAndPlugins, nonCheckedModules = descriptorMapWithBundled)) {
      val layout = descriptor.pluginLayout
      val suspicious = moreThanOneLayoutMap.values.filter { it.contains(layout) }
      if (suspicious.isNotEmpty()) {
        check(suspicious.size == 1) { "May have only one element: $suspicious" }
        val substitutor = moreThanOneLayoutSubstitutors.get(layout)
        if (substitutor != null) {
          Span.current().addEvent("Substituting plugin layout $layout with Marketplace-ready $substitutor")
          pluginsToPublish.add(substitutor)
        }
        else {
          errors.add(suspicious.first())
        }
      }
      else {
        pluginsToPublish.add(layout)
      }
    }
  }
  check(errors.isEmpty()) {
    "Attempt to publish plugins which have more than one layout and none of them are Marketplace-ready: $errors"
  }
}

private fun isPluginCompatible(
  plugin: PluginDescriptor,
  availableModulesAndPlugins: MutableSet<String>,
  nonCheckedModules: MutableMap<String, PluginDescriptor>,
): Boolean {
  nonCheckedModules.remove(plugin.id)
  for (declaredModule in plugin.declaredModules) {
    nonCheckedModules.remove(declaredModule)
  }
  for (requiredDependency in plugin.requiredDependencies) {
    if (availableModulesAndPlugins.contains(requiredDependency) || requiredDependency.startsWith("com.intellij.modules.os.")) {
      continue
    }

    val requiredPlugin = nonCheckedModules[requiredDependency]
    if (requiredPlugin != null && isPluginCompatible(requiredPlugin, availableModulesAndPlugins, nonCheckedModules)) {
      continue
    }

    Span.current().addEvent("${plugin.id} is not compatible because no required dependency is available: $requiredDependency")
    return false
  }
  for (incompatiblePlugin in plugin.incompatiblePlugins) {
    if (availableModulesAndPlugins.contains(incompatiblePlugin)) {
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
  context: BuildContext
): MutableMap<String, PluginDescriptor> {
  val pluginDescriptors = LinkedHashMap<String, PluginDescriptor>()
  val productLayout = context.productProperties.productLayout
  val nonTrivialPlugins = HashMap<String, PluginLayout>(productLayout.pluginLayouts.size)

  for (pluginLayout in productLayout.pluginLayouts) {
    nonTrivialPlugins.putIfAbsent(pluginLayout.mainModule, pluginLayout)
  }

  val allBundledPlugins = java.util.Set.copyOf(context.getBundledPluginModules())
  for (jpsModule in context.project.modules) {
    val moduleName = jpsModule.name
    if ((skipBundled && allBundledPlugins.contains(moduleName)) ||
        (honorCompatiblePluginsToIgnore && productLayout.compatiblePluginsToIgnore.contains(moduleName))) {
      continue
    }

    // when we migrate to Bazel, we will use a test marker to avoid checking the module name for "test" pattern
    if (moduleName.contains(".tests.") && !allBundledPlugins.contains(moduleName)) {
      continue
    }

    // not a plugin
    if (context.productProperties.platformPrefix != "FleetBackend" && moduleName.startsWith("fleet.plugins.")) {
      continue
    }

    val pluginXml = findFileInModuleSources(module = context.findRequiredModule(moduleName), relativePath = "META-INF/plugin.xml", onlyProductionSources = true) ?: continue

    val xml = JDOMUtil.load(pluginXml)
    check(!xml.isEmpty) {
      "Module '$moduleName': '$pluginXml' is empty"
    }

    if (skipImplementationDetails && xml.getAttributeValue("implementation-detail") == "true") {
      Span.current().addEvent(
        "skip module",
        Attributes.of(
          AttributeKey.stringKey("name"), moduleName,
          AttributeKey.stringKey("reason"), "'implementation-detail' == 'true'",
          AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
        )
      )
      continue
    }

    // a non-product plugin cannot include VCS and other such platform modules in the content
    if (xml.getChildren("content").any { contentElement ->
        contentElement.getChildren("module").any {
          val name = it.getAttributeValue("name", "")
          //intellij.platform.vcs.*.split modules are currently included in the CodeWithMe plugin
          name.startsWith("intellij.platform.vcs.") && !name.endsWith(".split") || name == "intellij.ide.startup.importSettings"
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
      continue
    }

    val pluginLayout = nonTrivialPlugins.get(moduleName) ?: PluginLayout.pluginAuto(listOf(moduleName))
    resolveNonXIncludeElement(original = xml, base = pluginXml, pathResolver = SourcesBasedXIncludeResolver(pluginLayout = pluginLayout, context = context))

    val id = xml.getChildTextTrim("id") ?: xml.getChildTextTrim("name")
    if (id.isNullOrEmpty()) {
      Span.current().addEvent(
        "skip module", Attributes.of(
        AttributeKey.stringKey("name"), moduleName,
        AttributeKey.stringKey("reason"), "does not contain <id/> element",
        AttributeKey.stringKey("pluginXml"), pluginXml.toString(),
      )
      )
      continue
    }

    if (id == "com.intellij.modules.ultimate" && !allBundledPlugins.contains(id)) {
      // if the 'ultimate' module is not mentioned in the list of bundled plugins,
      // then this module does not exist in a form of plugin in this distribution and should be ignored
      continue
    }

    val declaredModules = HashSet<String>()
    for (moduleElement in xml.getChildren("module")) {
      val value = moduleElement.getAttributeValue("value")
      if (value != null) {
        declaredModules.add(value)
      }
    }

    val content = xml.getChild("content")
    if (content != null) {
      for (module in content.getChildren("module")) {
        val name = module.getAttributeValue("name")
        if (name != null && !name.isEmpty()) {
          declaredModules.add(name)
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

    val description = xml.getChildTextTrim("description")
    val pluginDescriptor = PluginDescriptor(
      id, description, declaredModules, requiredDependencies, incompatiblePlugins, optionalDependencies, pluginLayout
    )
    pluginDescriptors.put(id, pluginDescriptor)
    for (module in declaredModules) {
      pluginDescriptors.put(module, pluginDescriptor)
    }
  }
  return pluginDescriptors
}

class PluginDescriptor(
  @JvmField val id: String,
  @JvmField val description: String?,
  @JvmField val declaredModules: Set<String>,
  @JvmField val requiredDependencies: Set<String>,
  @JvmField val incompatiblePlugins: Set<String>,
  @JvmField val optionalDependencies: List<Pair<String, String>>,
  @JvmField val pluginLayout: PluginLayout,
)

private class SourcesBasedXIncludeResolver(
  private val pluginLayout: PluginLayout,
  private val context: BuildContext,
) : XIncludePathResolver {
  override fun resolvePath(relativePath: String, base: Path?, isOptional: Boolean, isDynamic: Boolean): Path {
    var result: Path? = null
    for (moduleName in pluginLayout.includedModules.asSequence().map { it.moduleName }.distinct()) {
      result = context.findFileInModuleSources(moduleName, relativePath) ?: continue
    }
    return result ?: (if (base == null) Path.of(relativePath) else base.resolveSibling(relativePath))
  }
}
