/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

@CompileStatic
class PluginsCollector {
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

  @CompileDynamic
  private Map<String, PluginDescriptor> collectPluginDescriptors() {
    def pluginDescriptors = new HashMap<String, PluginDescriptor>()
    def productLayout = myBuildContext.productProperties.productLayout
    def nonTrivialPlugins = productLayout.allNonTrivialPlugins.groupBy { it.mainModule }
    def allBundledPlugins = productLayout.allBundledPluginsModules
    myBuildContext.project.modules.each {
      if (allBundledPlugins.contains(it.name)) {
        return
      }
      if (productLayout.compatiblePluginsToIgnore.contains(it.name)) {
        return
      }
      PluginLayout pluginLayout = nonTrivialPlugins[it.name]?.first()
      if (pluginLayout == null) {
        pluginLayout = PluginLayout.plugin(it.name)
      }
      def pluginXml = myBuildContext.findFileInModuleSources(it.name, "META-INF/plugin.xml")
      if (pluginXml == null) {
        return
      }

      def xml = new XmlParser().parse(pluginXml)
      String id = xml.id.text() ?: xml.name.text()
      if (!id) {
        return
      }
      def declaredModules = new HashSet()
      for (module in xml.module) {
        if (module.@value) {
          declaredModules += module.@value
        }
      }
      def requiredDependencies = new HashSet()
      for (dependency in xml.depends) {
        if (dependency.@optional != 'true') {
          requiredDependencies += dependency.text()
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

  private class PluginDescriptor {
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
}
