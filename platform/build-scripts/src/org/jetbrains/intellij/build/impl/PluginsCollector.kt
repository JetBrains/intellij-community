// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jdom.Namespace
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import java.io.IOException
import java.net.URL
import java.nio.file.Path
import java.util.*

fun collectCompatiblePluginsToPublish(builtinModuleData: BuiltinModulesFileData, context: BuildContext, result: MutableSet<PluginLayout>) {
  val availableModulesAndPlugins = HashSet<String>(builtinModuleData.modules.size + builtinModuleData.plugins.size)
  availableModulesAndPlugins.addAll(builtinModuleData.plugins)
  availableModulesAndPlugins.addAll(builtinModuleData.modules)

  val descriptorMap = collectPluginDescriptors(skipImplementationDetailPlugins = true,
                                               skipBundledPlugins = true,
                                               honorCompatiblePluginsToIgnore = true,
                                               context = context)
  val descriptorMapWithBundled = collectPluginDescriptors(skipImplementationDetailPlugins = true,
                                                          skipBundledPlugins = false,
                                                          honorCompatiblePluginsToIgnore = true,
                                                          context = context)

  // While collecting PluginDescriptor maps above, we may have chosen incorrect PluginLayout.
  // Let's check that and substitute incorrectly chosen one with more suitable one or report error.
  val moreThanOneLayoutMap = context.productProperties.productLayout.pluginLayouts.groupBy { it.mainModule }.filterValues { it.size > 1 }
  val moreThanOneLayoutSubstitutors = HashMap<PluginLayout, PluginLayout>()
  for ((module, layouts) in moreThanOneLayoutMap) {
    Span.current().addEvent("Module '$module' have ${layouts.size} layouts: $layouts")
    val substitutor = layouts.firstOrNull { it.bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE }
                      ?: layouts.firstOrNull { it.bundlingRestrictions == PluginBundlingRestrictions.NONE }
                      ?: continue
    layouts.forEach {
      if (it != substitutor) {
        moreThanOneLayoutSubstitutors.put(it, substitutor)
      }
    }
  }

  val errors = ArrayList<List<PluginLayout>>()
  for (descriptor in descriptorMap.values) {
    if (isPluginCompatible(descriptor, availableModulesAndPlugins, descriptorMapWithBundled)) {
      val layout = descriptor.pluginLayout
      val suspicious = moreThanOneLayoutMap.values.filter { it.contains(layout) }
      if (suspicious.isNotEmpty()) {
        check(suspicious.size == 1) { "May have only one element: $suspicious" }
        val substitutor = moreThanOneLayoutSubstitutors.get(layout)
        if (substitutor != null) {
          Span.current().addEvent("Substituting plugin layout $layout with Marketplace-ready $substitutor")
          result.add(substitutor)
        }
        else {
          errors.add(suspicious.first())
        }
      }
      else {
        result.add(layout)
      }
    }
  }
  check(errors.isEmpty()) {
    "Attempt to publish plugins which have more than one layout and none of them are Marketplace-ready: $errors"
  }
}

private fun isPluginCompatible(plugin: PluginDescriptor,
                               availableModulesAndPlugins: MutableSet<String>,
                               nonCheckedModules: MutableMap<String, PluginDescriptor>): Boolean {
  nonCheckedModules.remove(plugin.id)
  for (declaredModule in plugin.declaredModules) {
    nonCheckedModules.remove(declaredModule)
  }
  for (requiredDependency in plugin.requiredDependencies) {
    if (availableModulesAndPlugins.contains(requiredDependency) || requiredDependency.startsWith("com.intellij.platform.")) {
      continue
    }

    val requiredPlugin = nonCheckedModules.get(requiredDependency)
    if (requiredPlugin != null && isPluginCompatible(requiredPlugin, availableModulesAndPlugins, nonCheckedModules)) {
      continue
    }
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

fun collectPluginDescriptors(skipImplementationDetailPlugins: Boolean,
                             skipBundledPlugins: Boolean,
                             honorCompatiblePluginsToIgnore: Boolean,
                             context: BuildContext): MutableMap<String, PluginDescriptor> {
  val pluginDescriptors = LinkedHashMap<String, PluginDescriptor>()
  val productLayout = context.productProperties.productLayout
  val nonTrivialPlugins = HashMap<String, PluginLayout>(productLayout.pluginLayouts.size)

  for (pluginLayout in productLayout.pluginLayouts) {
    nonTrivialPlugins.putIfAbsent(pluginLayout.mainModule, pluginLayout)
  }

  val allBundledPlugins = HashSet(productLayout.bundledPluginModules)
  for (jpsModule in context.project.modules) {
    val moduleName = jpsModule.name
    if ((skipBundledPlugins && allBundledPlugins.contains(moduleName)) ||
        (honorCompatiblePluginsToIgnore && productLayout.compatiblePluginsToIgnore.contains(moduleName))) {
      continue
    }

    // not a plugin
    if (moduleName == "intellij.idea.ultimate.customization" ||
        moduleName == "intellij.lightEdit" ||
        (context.productProperties.platformPrefix != "FleetBackend" && moduleName.startsWith("fleet.plugins.")) ||
        moduleName == "intellij.webstorm") {
      continue
    }

    val pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml") ?: continue

    val xml = JDOMUtil.load(pluginXml)
    check(!xml.isEmpty) {
      "Module '$moduleName': '$pluginXml' is empty"
    }

    if (skipImplementationDetailPlugins && xml.getAttributeValue("implementation-detail") == "true") {
      Span.current().addEvent("skip module '$moduleName' since 'implementation-detail' == 'true' in '$pluginXml'")
      continue
    }

    val pluginLayout = nonTrivialPlugins.get(moduleName) ?: PluginLayout.pluginAuto(listOf(moduleName))
    JDOMXIncluder.resolveNonXIncludeElement(original = xml,
                                            base = pluginXml,
                                            pathResolver = SourcesBasedXIncludeResolver(pluginLayout = pluginLayout, context = context))

    val id = xml.getChildTextTrim("id") ?: xml.getChildTextTrim("name")
    check(!id.isNullOrEmpty()) {
      "Module '$moduleName': '$pluginXml' does not contain <id/> element"
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
        requiredDependencies += dependency.textTrim
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

    val pluginDescriptor = PluginDescriptor(id = id,
                                            description = xml.getChildTextTrim("description"),
                                            declaredModules = declaredModules,
                                            requiredDependencies = requiredDependencies,
                                            incompatiblePlugins = incompatiblePlugins,
                                            optionalDependencies = optionalDependencies,
                                            pluginLayout = pluginLayout)
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
) : JDOMXIncluder.PathResolver {
  override fun resolvePath(relativePath: String, base: URL?): URL {
    var result: URL? = null
    for (moduleName in pluginLayout.includedModules.asSequence().map { it.moduleName }.distinct()) {
      result = (context.findFileInModuleSources(moduleName, relativePath) ?: continue).toUri().toURL()
    }
    if (result == null) {
      result = if (base == null) URL(relativePath) else URL(base, relativePath)
    }
    return result
  }
}

private object JDOMXIncluder {
  /**
   * The original element will be mutated in place.
   */
  fun resolveNonXIncludeElement(original: Element, base: Path, pathResolver: PathResolver) {
    check(!isIncludeElement(original))
    val bases = ArrayDeque<URL>()
    bases.push(base.toUri().toURL())
    doResolveNonXIncludeElement(original = original, bases = bases, pathResolver = pathResolver)
  }

  private fun isIncludeElement(element: Element): Boolean {
    return element.name == "include" && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
  }

  private fun resolveXIncludeElement(element: Element, bases: Deque<URL>, pathResolver: PathResolver): MutableList<Element> {
    var base: URL? = null
    if (!bases.isEmpty()) {
      base = bases.peek()
    }

    val href = element.getAttributeValue("href")
    assert(href != null) { "Missing href attribute" }

    val baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
    if (baseAttribute != null) {
      base = URL(baseAttribute)
    }

    val remote = pathResolver.resolvePath(href, base)
    assert(!bases.contains(remote)) { "Circular XInclude Reference to ${remote.toExternalForm()}" }

    val fallbackElement = element.getChild("fallback", element.namespace)
    var remoteParsed = parseRemote(bases, remote, fallbackElement, pathResolver)
    if (!remoteParsed.isEmpty()) {
      remoteParsed = extractNeededChildren(element, remoteParsed)
    }

    var i = 0
    while (true) {
      if (i >= remoteParsed.size) {
        break
      }

      val o = remoteParsed.get(i)
      if (isIncludeElement(o)) {
        val elements = resolveXIncludeElement(o, bases, pathResolver)
        remoteParsed.addAll(i, elements)
        i += elements.size - 1
        remoteParsed.removeAt(i)
      }
      else {
        doResolveNonXIncludeElement(o, bases, pathResolver)
      }

      i++
    }

    remoteParsed.forEach(Element::detach)
    return remoteParsed
  }

  private fun extractNeededChildren(element: Element, remoteElements: List<Element>): MutableList<Element> {
    val xpointer = element.getAttributeValue("xpointer") ?: "xpointer(/idea-plugin/*)"

    var matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer)
    if (!matcher.matches()) {
      throw RuntimeException("Unsupported XPointer: $xpointer")
    }

    val pointer = matcher.group(1)
    matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
    if (!matcher.matches()) {
      throw RuntimeException("Unsupported pointer: $pointer")
    }

    val rootTagName = matcher.group(1)

    assert(remoteElements.size == 1)
    var e = remoteElements.get(0)
    if (e.name != rootTagName) {
      return mutableListOf()
    }

    val subTagName = matcher.group(2)
    if (subTagName != null) {
      // cut off the slash
      e = e.getChild(subTagName.substring(1))
      assert(e != null)
    }
    return e.children.toMutableList()
  }

  private fun parseRemote(bases: Deque<URL>,
                          remote: URL,
                          fallbackElement: Element?,
                          pathResolver: PathResolver): MutableList<Element> {
    try {
      bases.push(remote)
      val root = JDOMUtil.load(remote.openStream())
      return if (isIncludeElement(root)) {
        resolveXIncludeElement(root, bases, pathResolver)
      }
      else {
        doResolveNonXIncludeElement(root, bases, pathResolver)
        mutableListOf(root)
      }
    }
    catch (e: IOException) {
      if (fallbackElement != null) {
        return mutableListOf()
      }
      Span.current().addEvent("${remote.toExternalForm()} include ignored: ${e.message}")
      return mutableListOf()
    }
    finally {
      bases.pop()
    }
  }

  private fun doResolveNonXIncludeElement(original: Element, bases: Deque<URL>, pathResolver: PathResolver) {
    val contentList = original.content
    for (i in contentList.size - 1 downTo 0) {
      val content = contentList.get(i)
      if (content is Element) {
        if (isIncludeElement(content)) {
          original.setContent(i, resolveXIncludeElement(content, bases, pathResolver))
        }
        else {
          // process child element to resolve possible includes
          doResolveNonXIncludeElement(content, bases, pathResolver)
        }
      }
    }
  }

  interface PathResolver {
    fun resolvePath(relativePath: String, base: URL?): URL
  }
}