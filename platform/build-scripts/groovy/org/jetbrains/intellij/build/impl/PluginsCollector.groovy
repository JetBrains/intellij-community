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
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext

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
                                     @NotNull Map<String, PluginDescriptor> nonCheckedPlugins) {
    nonCheckedPlugins.remove(plugin.id)
    for (requiredDependency in plugin.requiredDependencies) {
      if (availableModulesAndPlugins.contains(requiredDependency)) {
        continue
      }
      def requiredPlugin = nonCheckedPlugins.get(requiredDependency)
      if (requiredPlugin != null && isPluginCompatible(requiredPlugin, availableModulesAndPlugins, nonCheckedPlugins)) {
        continue
      }
      return false
    }
    availableModulesAndPlugins.add(plugin.id)
    return true
  }

  private Map<String, PluginDescriptor> collectPluginDescriptors() {
    def pluginDescriptors = new HashMap<String, PluginDescriptor>()
    def productLayout = myBuildContext.productProperties.productLayout
    def nonTrivialPlugins = productLayout.allNonTrivialPlugins.groupBy { it.mainModule }
    myBuildContext.project.modules.each {
      if (productLayout.bundledPluginModules.contains(it.name)) {
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
      def requiredDependencies = new HashSet()
      for (dependency in xml.depends) {
        if (dependency.@optional != 'true') {
          requiredDependencies += dependency.text()
        }
      }

      pluginDescriptors[id] = new PluginDescriptor(id, requiredDependencies, pluginLayout)
    }
    return pluginDescriptors
  }

  private class PluginDescriptor {
    private final String id
    private final Set<String> requiredDependencies
    private final PluginLayout pluginLayout

    PluginDescriptor(String id, Set<String> requiredDependencies, PluginLayout pluginLayout) {
      this.id = id
      this.requiredDependencies = requiredDependencies
      this.pluginLayout = pluginLayout
    }
  }
}
