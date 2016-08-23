/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.MultiValuesMap
import groovy.transform.Immutable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Described layout of a plugin in the product distribution
 *
 * @author nik
 */
class PluginLayout extends BaseLayout {
  final String mainModule
  String directoryName
  final Set<String> optionalModules = new LinkedHashSet<>()
  private boolean doNotCreateSeparateJarForLocalizableResources

  private PluginLayout(String mainModule) {
    this.mainModule = mainModule
  }

/**
   * Creates the plugin layout description. The default plugin layout is composed of a jar with name {@code mainModuleName}.jar containing output of
   * {@code mainModuleName}, resources_en.jar containing translatable resources from {@code mainModuleName}, and the module libraries of
   * {@code mainModuleName} with scopes 'Compile' and 'Runtime' placed under 'lib' directory in a directory with name {@code mainModuleName}.
   * In you need to include additional resources or modules into the plugin layout specify them in {@code body} parameter.
   *
   * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
   */
  static PluginLayout plugin(String mainModuleName, @DelegatesTo(PluginLayoutSpec) Closure body = {}) {
    def layout = new PluginLayout(mainModuleName)
    def spec = new PluginLayoutSpec(layout)
    body.delegate = spec
    body()
    layout.directoryName = spec.directoryName
    spec.withModule(mainModuleName, spec.mainJarName)
    if (layout.doNotCreateSeparateJarForLocalizableResources) {
      layout.modulesWithLocalizableResourcesInCommonJar.clear()
    }
    return layout
  }

  MultiValuesMap<String, String> getActualModules(Set<String> enabledPluginModules) {
    def result = new MultiValuesMap<String, String>(true)
    for (Map.Entry<String, Collection<String>> entry : moduleJars.entrySet()) {
      for (String moduleName : entry.getValue()) {
        if (!optionalModules.contains(moduleName) || enabledPluginModules.contains(moduleName)) {
          result.put(entry.key, moduleName)
        }
      }
    }
    return result
  }

  @Override
  String basePath(BuildContext buildContext) {
    JpsPathUtil.urlToPath(buildContext.findRequiredModule(mainModule).contentRootsList.urls.first())
  }

  static class PluginLayoutSpec extends BaseLayoutSpec {
    private final PluginLayout layout
    /**
     * Name of the directory (under 'plugins' directory) where the plugin should be placed
     */
    String directoryName
    /**
     * Name of the main plugin JAR file
     */
    String mainJarName

    PluginLayoutSpec(PluginLayout layout) {
      super(layout)
      this.layout = layout
      directoryName = layout.mainModule
      mainJarName = "${layout.mainModule}.jar"
    }

    /**
     * Register an optional module which may be excluded from the plugin distribution in some products
     */
    void withOptionalModule(String moduleName, String relativeJarPath = "${moduleName}.jar") {
      layout.optionalModules << moduleName
      withModule(moduleName, relativeJarPath)
    }

    void withJpsModule(String moduleName) {
      withModule(moduleName, "jps/${moduleName}.jar")
    }


    /**
     * Do not create 'resources_en.jar' and pack all resources into corresponding module JARs.
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCreateSeparateJarForLocalizableResources() {
      layout.doNotCreateSeparateJarForLocalizableResources = true
    }
  }
}