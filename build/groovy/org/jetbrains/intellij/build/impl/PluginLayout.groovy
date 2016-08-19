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

/**
 * @author nik
 */
class PluginLayout {
  final String mainModule
  String directoryName
  /** JAR name (or path relative to 'lib' directory) to module name */
  final MultiValuesMap<String, String> moduleJars = new MultiValuesMap<>(true)
  /** source directory -> relative path to a target directory under the plugin directory */
  final Map<String, String> resourcePaths = [:]
  /** source directory -> relative path to a zip file under the plugin directory */
  final Map<String, String> resourceArchivePaths = [:]
  /** module name to entries which should be excluded from its output */
  final MultiValuesMap<String, String> moduleExcludes = new MultiValuesMap<>(true)
  final List<String> includedProjectLibraries = []
  final List<ModuleLibraryData> includedModuleLibraries = []
  final Set<String> optionalModules = new LinkedHashSet<>()
  private final Set<String> modulesWithLocalizableResourcesInCommonJar = new LinkedHashSet<>()
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

  boolean packLocalizableResourcesInCommonJar(String moduleName) {
    return modulesWithLocalizableResourcesInCommonJar.contains(moduleName)
  }

  static class PluginLayoutSpec {
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

    /**
     * Register an additional module to be included into the plugin distribution. If {@code relativeJarPath} doesn't contain '/' (i.e. the
     * JAR will be added to the plugin's classpath) this will also cause modules library from {@code moduleName} with scopes 'Compile' and
     * 'Runtime' to be copied to the 'lib' directory of the plugin.
     *
     * @param relativeJarPath target JAR path relative to 'lib' directory of the plugin; different modules may be packed into the same JAR,
     * but don't use this for new plugins; this parameter is temporary added to keep layout of old plugins.
     * @param localizableResourcesInCommonJar if {@code true} the translatable resources from the module (messages, inspection descriptions, etc) will be
     * placed into a separate 'resources_en.jar'. <strong>Do not use this for new plugins, this parameter is temporary added to keep layout of old plugins</strong>.
     */
    void withModule(String moduleName, String relativeJarPath = "${moduleName}.jar", boolean localizableResourcesInCommonJar = true) {
      if (localizableResourcesInCommonJar) {
        layout.modulesWithLocalizableResourcesInCommonJar << moduleName
      }
      layout.moduleJars.put(relativeJarPath, moduleName)
    }

    void withJpsModule(String moduleName) {
      withModule(moduleName, "jps/${moduleName}.jar")
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputDirectory target path relative to the plugin root directory
     */
    void withResource(String resourcePath, String relativeOutputDirectory) {
      layout.resourcePaths[resourcePath] = relativeOutputDirectory
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    void withResourceArchive(String resourcePath, String relativeOutputFile) {
      layout.resourceArchivePaths[resourcePath] = relativeOutputFile
    }

    /**
     * Include the project library to 'lib' directory of the plugin distribution
     */
    void withProjectLibrary(String libraryName) {
      layout.includedProjectLibraries << libraryName
    }

    /**
     * Include the module library to the plugin distribution. Please note that it makes sense to call this method only
     * for additional modules which aren't copied directly to the 'lib' directory of the plugin distribution, because for ordinary modules
     * their module libraries are included into the layout automatically.
     */
    void withModuleLibrary(String libraryName, String moduleName = layout.mainModule, String relativeOutputPath) {
      layout.includedModuleLibraries << new ModuleLibraryData(moduleName: moduleName, libraryName: libraryName, relativeOutputPath: relativeOutputPath)
    }

    /**
     * Exclude the specified directory when {@code moduleName} is packed into JAR file.
     * <strong>This is a temporary method added to keep layout of some old plugins. If some directory from a module shouldn't be included into the
     * module JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
     * @param excludedDirectory path to the directory to be exclude relatively to the module output root
     */
    void excludeFromModule(String moduleName, String excludedDirectory) {
      layout.moduleExcludes.put(moduleName, excludedDirectory)
    }

    /**
     * Do not create 'resources_en.jar' and pack all resources into corresponding module JARs.
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCreateSeperateJarForLocalizableResources() {
      layout.doNotCreateSeparateJarForLocalizableResources = true
    }
  }

  @Immutable
  static class ModuleLibraryData {
    String moduleName
    String libraryName
    String relativeOutputPath
  }
}