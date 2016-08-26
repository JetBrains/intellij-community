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



package org.jetbrains.intellij.build

import com.intellij.openapi.util.MultiValuesMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

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
   * Names of the project libraries which JARs' contents should be extracted into {@link #mainJarName} JAR.
   */
  List<String> projectLibrariesToUnpackIntoMainJar = []

  /**
   * Maps names of JARs to names of the modules; these modules will be packed into these JARs and copied to the product's 'lib' directory.
   */
  MultiValuesMap<String, String> additionalPlatformJars = new MultiValuesMap<>(true)

  /** Module name to entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep layout of some products. If some directory from a module shouldn't be included into the
   * product JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
   */
  MultiValuesMap<String, String> moduleExcludes = new MultiValuesMap<>(true)

  /**
   * Name of the module which classpath will be used to build searchable options index
   */
  String mainModule

  /**
   * Name of the module containing search/searchableOptions.xml file.
   */
  String searchableOptionsModule = "platform-resources"

  /**
   * Paths to license files which are required to start IDE in headless mode to generate searchable options index
   */
  List<String> licenseFilesToBuildSearchableOptions = []

  /**
   * @param allPlugins descriptions of layout of all plugins which may be included into the product
   * @return list of all modules which output is included into the plugin's JARs
   */
  List<String> getIncludedPluginModules(List<PluginLayout> allPlugins) {
    Set<String> enabledPluginModules = getEnabledPluginModules()
    allPlugins.findAll { enabledPluginModules.contains(it.mainModule) }.collectMany { it.getActualModules(enabledPluginModules).values() }
  }

  List<String> getIncludedPlatformModules() {
    platformApiModules + platformImplementationModules + additionalPlatformJars.values()
  }

  Set<String> getEnabledPluginModules() {
    (bundledPluginModules + pluginModulesToPublish) as Set<String>
  }
}