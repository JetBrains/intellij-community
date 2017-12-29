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
import com.intellij.openapi.util.Pair
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ResourcesGenerator

import java.util.function.Function

/**
 * Describes layout of a plugin in the product distribution
 *
 * @author nik
 */
class PluginLayout extends BaseLayout {
  final String mainModule
  String directoryName
  final Set<String> optionalModules = new LinkedHashSet<>()
  private boolean doNotCreateSeparateJarForLocalizableResources
  Function<BuildContext, String> versionEvaluator = { BuildContext context -> context.buildNumber } as Function<BuildContext, String>

  private PluginLayout(String mainModule) {
    this.mainModule = mainModule
  }

  /**
   * Creates the plugin layout description. The default plugin layout is composed of a jar with name {@code mainModuleName}.jar containing
   * production output of {@code mainModuleName} module, resources_en.jar containing translatable resources from {@code mainModuleName},
   * and the module libraries of {@code mainModuleName} with scopes 'Compile' and 'Runtime' placed under 'lib' directory in a directory
   * with name {@code mainModuleName}. In you need to include additional resources or modules into the plugin layout specify them in
   * {@code body} parameter. If you don't need to change the default layout there is no need to call this method at all, it's enough to
   * specify the plugin module in {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundledPluginModules/pluginModulesToPublish} list.
   *
   * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
   */
  static PluginLayout plugin(String mainModuleName, @DelegatesTo(PluginLayoutSpec) Closure body = {}) {
    def layout = new PluginLayout(mainModuleName)
    def spec = new PluginLayoutSpec(layout)
    body.delegate = spec
    body()
    layout.directoryName = spec.directoryName
    if (spec.version != null) {
      layout.versionEvaluator = { BuildContext context -> spec.version } as Function<BuildContext, String>
    }
    spec.withModule(mainModuleName, spec.mainJarName)
    if (layout.doNotCreateSeparateJarForLocalizableResources) {
      layout.modulesWithLocalizableResourcesInCommonJar.clear()
    }
    return layout
  }

  /**
   * @return map from a JAR name to list of modules
   */
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

  static class PluginLayoutSpec extends BaseLayoutSpec {
    private final PluginLayout layout
    /**
     * Custom name of the directory (under 'plugins' directory) where the plugin should be placed. By default the main module name is used.
     * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
     */
    String directoryName
    /**
     * Custom name of the main plugin JAR file. By default the main module name with 'jar' extension is used.
     * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
     */
    String mainJarName

    /**
     * @deprecated use {@link #withCustomVersion(java.util.function.Function)} instead
     */
    String version

    PluginLayoutSpec(PluginLayout layout) {
      super(layout)
      this.layout = layout
      directoryName = layout.mainModule
      mainJarName = "${layout.mainModule}.jar"
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    void withResource(String resourcePath, String relativeOutputPath) {
      withResourceFromModule(layout.mainModule, resourcePath, relativeOutputPath)
    }

    /**
     * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    void withResourceFromModule(String moduleName, String resourcePath, String relativeOutputPath) {
      layout.resourcePaths << new ModuleResourceData(moduleName, resourcePath, relativeOutputPath, false)
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    void withResourceArchive(String resourcePath, String relativeOutputFile) {
      layout.resourcePaths << new ModuleResourceData(layout.mainModule, resourcePath, relativeOutputFile, true)
    }

    /**
     * Copy output produced by {@code generator} to the directory specified by {@code relativeOutputPath} under the plugin directory.
     */
    void withGeneratedResources(ResourcesGenerator generator, String relativeOutputPath) {
      layout.resourceGenerators << Pair.create(generator, relativeOutputPath)
    }

    /**
     * Register an optional module which may be excluded from the plugin distribution in some products. These modules are included in plugin
     * distribution only if they are added to {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules} list.
     */
    void withOptionalModule(String moduleName, String relativeJarPath = "${moduleName}.jar") {
      layout.optionalModules << moduleName
      withModule(moduleName, relativeJarPath)
    }

    void withJpsModule(String moduleName) {
      withModule(moduleName, "jps/${moduleName}.jar")
    }

    /**
     * By default version of a plugin is equal to the version of the IDE it's built with. This method allows to specify custom version evaluator.
     * <strong>Don't use this for new plugins</strong>; it is temporary added to keep versioning scheme for some old plugins.
     */
    void withCustomVersion(Function<BuildContext, String> versionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    /**
     * Do not create 'resources_en.jar' and pack all resources into corresponding module JARs.
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCreateSeparateJarForLocalizableResources() {
      layout.doNotCreateSeparateJarForLocalizableResources = true
    }

    /**
     * Do not automatically include module libraries from {@code moduleNames}
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCopyModuleLibrariesAutomatically(List<String> moduleNames) {
      layout.modulesWithExcludedModuleLibraries.addAll(moduleNames)
    }
  }
}