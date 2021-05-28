// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Ref
import com.intellij.util.xmlb.JDOMXIncluder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

@CompileStatic
final class PluginsCollector {
  private final String myProvidedModulesFilePath
  private final BuildContext myBuildContext

  PluginsCollector(@NotNull BuildContext buildContext, @NotNull String providedModulesFilePath) {
    this.myBuildContext = buildContext
    this.myProvidedModulesFilePath = providedModulesFilePath
  }

  List<PluginLayout> collectCompatiblePluginsToPublish() {
    def parse = new JsonSlurper().parse(new File(myProvidedModulesFilePath)) as Map
    Set<String> availableModulesAndPlugins = new HashSet<String>(parse['modules'] as Collection)
    availableModulesAndPlugins.addAll(parse['plugins'] as Collection)

    def descriptorsMap = collectPluginDescriptors()
    def pluginDescriptors = new HashSet<PluginDescriptor>(descriptorsMap.values())
    return pluginDescriptors.findAll { isPluginCompatible(it, availableModulesAndPlugins, descriptorsMap) }.collect { it.pluginLayout }
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
    availableModulesAndPlugins.add(plugin.id)
    availableModulesAndPlugins.addAll(plugin.declaredModules)
    return true
  }

  private @NotNull Map<String, PluginDescriptor> collectPluginDescriptors() {
    def pluginDescriptors = new HashMap<String, PluginDescriptor>()
    def productLayout = myBuildContext.productProperties.productLayout
    def nonTrivialPlugins = productLayout.allNonTrivialPlugins.groupBy { it.mainModule }
    def allBundledPlugins = productLayout.bundledPluginModules as Set<String>
    for (JpsModule  jpsModule : myBuildContext.project.modules) {
      if (allBundledPlugins.contains(jpsModule.name) || productLayout.compatiblePluginsToIgnore.contains(jpsModule.name)) {
        continue
      }

      // Not a plugin
      if (jpsModule.name == "intellij.idea.ultimate.resources") {
        continue
      }

      PluginLayout pluginLayout = nonTrivialPlugins[jpsModule.name]?.first()
      if (pluginLayout == null) {
        pluginLayout = PluginLayout.plugin(jpsModule.name)
      }

      Path pluginXml = myBuildContext.findFileInModuleSources(jpsModule.name, "META-INF/plugin.xml")
      if (pluginXml == null) {
        continue
      }

      Element xml = JDOMUtil.load(pluginXml)
      if (JDOMUtil.isEmpty(xml)) {
        // Throws an exception
        myBuildContext.messages.error("Module '$jpsModule.name': '$pluginXml' is empty")
        continue
      }

      if (xml.getAttributeValue('implementation-detail') == 'true') {
        myBuildContext.messages.debug("PluginsCollector: skipping module '$jpsModule.name' since 'implementation-detail' == 'true' in '$pluginXml'")
        continue
      }

      JDOMXIncluder.resolveNonXIncludeElement(xml, pluginXml.toUri().toURL(), true, new SourcesBasedXIncludeResolver(jpsModule))

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
      def requiredDependencies = new HashSet<String>()
      for (dependency in xml.getChildren('depends')) {
        if (dependency.getAttributeValue('optional') != 'true') {
          requiredDependencies += dependency.getTextTrim()
        }
      }

      def pluginDescriptor = new PluginDescriptor(id, declaredModules, requiredDependencies, pluginLayout)
      pluginDescriptors[id] = pluginDescriptor
      for (module in declaredModules) {
        pluginDescriptors[module] = pluginDescriptor
      }
    }
    return pluginDescriptors
  }

  private static final class PluginDescriptor {
    private final String id
    private final Set<String> declaredModules
    private final Set<String> requiredDependencies
    private final PluginLayout pluginLayout

    PluginDescriptor(String id, Set<String> declaredModules, Set<String> requiredDependencies, PluginLayout pluginLayout) {
      this.id = id
      this.declaredModules = declaredModules
      this.requiredDependencies = requiredDependencies
      this.pluginLayout = pluginLayout
    }
  }

  private static final class SourcesBasedXIncludeResolver implements JDOMXIncluder.PathResolver {
    private final JpsModule myMainModule

    SourcesBasedXIncludeResolver(@NotNull JpsModule mainModule) {
      myMainModule = mainModule
    }

    @Override
    URL resolvePath(@NotNull String relativePath, @Nullable URL url) throws MalformedURLException {
      Ref<URL> result = Ref.create()
      JpsJavaExtensionService.dependencies(myMainModule).recursively().processModules({ module ->
        for (def sourceRoot : module.sourceRoots) {
          def resolved = new File(sourceRoot.file, relativePath)
          if (resolved.exists()) {
            result.set(resolved.toURI().toURL())
            return false
          }
        }
        return true
      })
      return result.isNull() ? JDOMXIncluder.DEFAULT_PATH_RESOLVER.resolvePath(relativePath, url) : result.get()
    }
  }
}