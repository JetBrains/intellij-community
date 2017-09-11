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
package org.jetbrains.intellij.build

import com.intellij.openapi.util.MultiValuesMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout

import java.util.function.Consumer

/**
 * @author nik
 */
@CompileStatic
class ProductModulesLayout {
  /**
   * Name of the main product JAR file. Outputs of {@link #platformImplementationModules} will be packed into it.
   */
  String mainJarName

  /**
   * Names of the modules which need to be packed into openapi.jar in the product's 'lib' directory.
   * @see CommunityRepositoryModules#PLATFORM_API_MODULES
   */
  List<String> platformApiModules = []

  /**
   * Names of the modules which need to be included into {@link #mainJarName} in the product's 'lib' directory
   * @see CommunityRepositoryModules#PLATFORM_IMPLEMENTATION_MODULES
   */
  List<String> platformImplementationModules = []

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which need to be bundled with the product. It may also
   * includes names of optional modules (added via {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#withOptionalModule})
   * from these plugins which need to be included into the plugin distribution for this product.
   */
  List<String> bundledPluginModules = []

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed
   * into it. Zip archives of these plugins will be built and placed under 'plugins' directory in the build artifacts.
   */
  List<String> pluginModulesToPublish = []

  /**
   * Describes non-trivial layout of all plugins which may be included into the product. The actual list of the plugins need to be bundled
   * with the product is specified by {@link #bundledPluginModules}. There is no need to specify layout for plugins where it's trivial,
   * i.e. for plugins which include an output of a single module and its module libraries, it's enough to specify module names of such plugins
   * in {@link #bundledPluginModules}.
   */
  List<PluginLayout> allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS

  /**
   * Names of the project libraries which JARs' contents should be extracted into {@link #mainJarName} JAR.
   */
  List<String> projectLibrariesToUnpackIntoMainJar = []

  /**
   * Maps names of JARs to names of the modules; these modules will be packed into these JARs and copied to the product's 'lib' directory.
   */
  MultiValuesMap<String, String> additionalPlatformJars = new MultiValuesMap<>(true)

  /**
   * Module name to list of Ant-like patterns describing entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep layout of some products. If some directory from a module shouldn't be included into the
   * product JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
   */
  MultiValuesMap<String, String> moduleExcludes = new MultiValuesMap<>(true)

  /**
   * Additional customizations of platform JARs. <strong>This is a temporary property added to keep layout of some products.</strong>
   */
  Consumer<PlatformLayout> platformLayoutCustomizer = {} as Consumer<PlatformLayout>

  /**
   * Names of the modules which classpath will be used to build searchable options index <br>
   * //todo[nik] get rid of this property and automatically include all platform and plugin modules to the classpath when building searchable options index
   */
  List<String> mainModules = []

  /**
   * Name of the module containing search/searchableOptions.xml file.
   */
  String searchableOptionsModule = "platform-resources"

  /**
   * Paths to license files which are required to start IDE in headless mode to generate searchable options index
   */
  List<String> licenseFilesToBuildSearchableOptions = []

  /**
   * If {@code true} a special xml descriptor in custom plugin repository format will be generated for {@link #pluginModulesToPublish} plugins.
   * This descriptor and the plugin *.zip files need to be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file.
   */
  boolean prepareCustomPluginRepositoryForPublishedPlugins = false

  /**
   * If {@code true} then all plugins that compatible with an IDE will be built.
   * Otherwise only plugins from {@link #pluginModulesToPublish} will be considered.
   */
  boolean buildAllCompatiblePlugins = false

  /**
   * List of plugin names which should not be built even if they are compatible and {@link #buildAllCompatiblePlugins} is true
   */
  List<String> compatiblePluginsToIgnore = []

  /**
   * Names of the main modules of plugins from {@link #pluginModulesToPublish} list where since-build/until-build range should be restricted.
   * These plugins will be compatible with builds which number differ from the build which produces these plugins only in the last component,
   * i.e. plugins produced in 163.1111.22 build will be compatible with 163.1111.* builds. The plugins not included into this list
   * will be compatible with all builds from the same baseline, i.e. with 163.* builds.
   */
  List<String> pluginModulesWithRestrictedCompatibleBuildRange = []

  /**
   * Specifies path to a text file containing list of classes in order they are loaded by the product. Entries in the produces *.jar files
   * will be reordered accordingly to reduct IDE startup time. If {@code null} no reordering will be performed.
   */
  String classesLoadingOrderFilePath = null

  /**
   * @return list of all modules which output is included into the plugin's JARs
   */
  List<String> getIncludedPluginModules(Set<String> enabledPluginModules) {
    def modulesFromNonTrivialPlugins = allNonTrivialPlugins.findAll { enabledPluginModules.contains(it.mainModule) }.
      collectMany { it.getActualModules(enabledPluginModules).values() }
    (enabledPluginModules + modulesFromNonTrivialPlugins) as List<String>
  }

  List<String> getIncludedPlatformModules() {
    platformApiModules + platformImplementationModules + additionalPlatformJars.values()
  }
}