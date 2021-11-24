// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.util.xmlb.JDOMXIncluder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

@CompileStatic
final class PluginsCollector {
  private final BuildContext myBuildContext

  PluginsCollector(@NotNull BuildContext buildContext) {
    myBuildContext = buildContext
  }

  List<PluginLayout> collectCompatiblePluginsToPublish(String providedModulesFilePath) {
    def parse = new JsonSlurper().parse(new File(providedModulesFilePath)) as Map
    Set<String> availableModulesAndPlugins = new HashSet<String>(parse['modules'] as Collection)
    availableModulesAndPlugins.addAll(parse['plugins'] as Collection)

    def descriptorsMap = collectPluginDescriptors(true, true, true)
    def descriptorsMapWithBundled = collectPluginDescriptors(true, false, true)
    def pluginDescriptors = new HashSet<PluginDescriptor>(descriptorsMap.values())
    return pluginDescriptors.findAll { isPluginCompatible(it, availableModulesAndPlugins, descriptorsMapWithBundled) }.collect { it.pluginLayout }
  }

  private boolean isPluginCompatible(@NotNull PluginDescriptor plugin,
                                     @NotNull Set<String> availableModulesAndPlugins,
                                     @NotNull Map<String, PluginDescriptor> nonCheckedModules) {
    nonCheckedModules.remove(plugin.id)
    for (declaredModule in plugin.declaredModules) {
      nonCheckedModules.remove(declaredModule)
    }
    for (requiredDependency in plugin.requiredDependencies) {
      if (availableModulesAndPlugins.contains(requiredDependency)) {
        continue
      }
      def requiredPlugin = nonCheckedModules.get(requiredDependency)
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

  @NotNull Map<String, PluginDescriptor> collectPluginDescriptors(boolean skipImplementationDetailPlugins, boolean skipBundledPlugins,
                                                                  boolean honorCompatiblePluginsToIgnore) {
    def pluginDescriptors = new HashMap<String, PluginDescriptor>()
    def productLayout = myBuildContext.productProperties.productLayout
    def nonTrivialPlugins = productLayout.allNonTrivialPlugins.groupBy { it.mainModule }
    Set<String> allBundledPlugins = new HashSet<>(productLayout.bundledPluginModules)
    for (JpsModule  jpsModule : myBuildContext.project.modules) {
      if (skipBundledPlugins && allBundledPlugins.contains(jpsModule.name) ||
          honorCompatiblePluginsToIgnore && productLayout.compatiblePluginsToIgnore.contains(jpsModule.name)) {
        continue
      }

      // not a plugin
      if (jpsModule.name == "intellij.idea.ultimate.resources" || jpsModule.name == "intellij.lightEdit" || jpsModule.name == "intellij.webstorm") {
        continue
      }

      Path pluginXml = myBuildContext.findFileInModuleSources(jpsModule.name, "META-INF/plugin.xml")
      if (pluginXml == null) {
        continue
      }

      PluginLayout pluginLayout = nonTrivialPlugins.get(jpsModule.name)?.first()
      if (pluginLayout == null) {
        pluginLayout = PluginLayout.plugin(jpsModule.name)
      }

      Element xml = JDOMUtil.load(pluginXml)
      if (JDOMUtil.isEmpty(xml)) {
        // Throws an exception
        myBuildContext.messages.error("Module '$jpsModule.name': '$pluginXml' is empty")
        continue
      }

      if (skipImplementationDetailPlugins && xml.getAttributeValue("implementation-detail") == "true") {
        myBuildContext.messages.debug("PluginsCollector: skipping module '$jpsModule.name' since 'implementation-detail' == 'true' in '$pluginXml'")
        continue
      }

      JDOMXIncluder.resolveNonXIncludeElement(xml, pluginXml.toUri().toURL(), true, new SourcesBasedXIncludeResolver(pluginLayout, myBuildContext))
      //this code is temporary added to fix problems with xi:include tags without xpointer attribute; JDOMXIncluder keeps top-level idea-plugin tag for them
      //todo move PathBasedJdomXIncluder.kt to util module and reuse it here
      while (!xml.getChildren("idea-plugin").isEmpty()) {
        List<Element> contentOfIncludes = xml.getChildren("idea-plugin").collectMany { it.children as Collection<Element> }
        contentOfIncludes.forEach { Element child ->
          child.detach()
        }
        xml.removeChildren("idea-plugin")
        contentOfIncludes.forEach { Element child ->
          xml.addContent(child)
        }
      }

      String id = xml.getChildTextTrim("id") ?: xml.getChildTextTrim("name")
      if (id == null || id.isEmpty()) {
        // Throws an exception
        myBuildContext.messages.error("Module '$jpsModule.name': '$pluginXml' does not contain <id/> element")
        continue
      }

      def declaredModules = new HashSet<String>()
      for (moduleElement in xml.getChildren('module')) {
        def value = moduleElement.getAttributeValue('value')
        if (value) {
          declaredModules.add(value)
        }
      }
      def content = xml.getChild('content')
      if (content) {
        for (module in content.getChildren('module')) {
          def moduleName = module.getAttributeValue('name')
          if (moduleName) {
            declaredModules += moduleName
          }
        }
      }

      def requiredDependencies = new HashSet<String>()
      def optionalDependencies = new ArrayList<Pair<String, String>>()
      for (dependency in xml.getChildren('depends')) {
        if (dependency.getAttributeValue('optional') != 'true') {
          requiredDependencies += dependency.getTextTrim()
        }
        else {
          optionalDependencies += new Pair(dependency.getTextTrim(), dependency.getAttributeValue("config-file"))
        }
      }
      def dependencies = xml.getChild('dependencies')
      if (dependencies != null) {
        for (plugin in dependencies.getChildren('plugin')) {
          def pluginId = plugin.getAttributeValue('id')
          if (pluginId) {
            requiredDependencies += pluginId
          }
        }
        for (module in dependencies.getChildren('module')) {
          def moduleName = module.getAttributeValue('name')
          if (moduleName) {
            requiredDependencies += moduleName
          }
        }
      }

      def incompatiblePlugins = new HashSet<String>()
      for (pluginId in xml.getChildren('incompatible-with')) {
        incompatiblePlugins += pluginId.getTextTrim()
      }

      String description = xml.getChildTextTrim("description")
      def pluginDescriptor = new PluginDescriptor(id, description, declaredModules, requiredDependencies, incompatiblePlugins, optionalDependencies, pluginLayout)
      pluginDescriptors[id] = pluginDescriptor
      for (module in declaredModules) {
        pluginDescriptors[module] = pluginDescriptor
      }
    }
    return pluginDescriptors
  }

  static final class PluginDescriptor {
    final String id
    final String description
    final Set<String> declaredModules
    final Set<String> requiredDependencies
    final Set<String> incompatiblePlugins
    final List<Pair<String, String>> optionalDependencies
    final PluginLayout pluginLayout

    PluginDescriptor(String id,
                     String description,
                     Set<String> declaredModules,
                     Set<String> requiredDependencies,
                     Set<String> incompatiblePlugins,
                     List<Pair<String, String>> optionalDependencies,
                     PluginLayout pluginLayout) {
      this.id = id
      this.description = description
      this.declaredModules = declaredModules
      this.requiredDependencies = requiredDependencies
      this.incompatiblePlugins = incompatiblePlugins
      this.optionalDependencies = optionalDependencies
      this.pluginLayout = pluginLayout
    }
  }

  private static final class SourcesBasedXIncludeResolver implements JDOMXIncluder.PathResolver {
    private final PluginLayout myPluginLayout
    private final BuildContext myBuildContext

    SourcesBasedXIncludeResolver(@NotNull PluginLayout pluginLayout, @NotNull BuildContext buildContext) {
      myPluginLayout = pluginLayout
      this.myBuildContext = buildContext
    }

    @Override
    URL resolvePath(@NotNull String relativePath, @Nullable URL url) throws MalformedURLException {
      URL result = null
      for (moduleName in myPluginLayout.includedModuleNames) {
        def path = myBuildContext.findFileInModuleSources(moduleName, relativePath)
        if (path != null) {
          result = path.toUri().toURL()
        }
      }
      if (result == null) {
        result = JDOMXIncluder.DEFAULT_PATH_RESOLVER.resolvePath(relativePath, url)
      }
      if (result == null) {
        throw new IllegalArgumentException("Cannot resolve path $relativePath in ${myPluginLayout.mainModule}")
      }
      return result
    }
  }
}