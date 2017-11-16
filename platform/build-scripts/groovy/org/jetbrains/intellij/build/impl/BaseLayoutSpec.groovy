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

/**
 * @author nik
 */
class BaseLayoutSpec {
  protected final BaseLayout layout

  BaseLayoutSpec(BaseLayout layout) {
    this.layout = layout
  }

  /**
   * @deprecated use explicit resource name instead of boolean or null. To be removed in IDEA 2018.3.
   */
  void withModule(String moduleName, String relativeJarPath = "${moduleName}.jar", boolean localizableResourcesInCommonJar) {
    if (localizableResourcesInCommonJar) {
      withModule(moduleName, relativeJarPath)
    } else {
      withModule(moduleName, relativeJarPath, null)
    }
  }

  /**
   * Register an additional module to be included into the plugin distribution. If {@code relativeJarPath} doesn't contain '/' (i.e. the
   * JAR will be added to the plugin's classpath) this will also cause modules library from {@code moduleName} with scopes 'Compile' and
   * 'Runtime' to be copied to the 'lib' directory of the plugin.
   *
   * @param relativeJarPath target JAR path relative to 'lib' directory of the plugin; different modules may be packed into the same JAR,
   * but <strong>don't use this for new plugins</strong>; this parameter is temporary added to keep layout of old plugins.
   * @param localizableResourcesInCommonJar if {@code true} the translatable resources from the module (messages, inspection descriptions, etc) will be
   * placed into a separate 'resources_en.jar'. <strong>Do not use this for new plugins, this parameter is temporary added to keep layout of old plugins</strong>.
   */
  void withModule(String moduleName, String relativeJarPath = "${moduleName}.jar", String localizableResourcesInJar = "resources_en.jar") {
    if (localizableResourcesInJar != null) {
      layout.modulesWithLocalizableResourcesInCommonJar.put(moduleName, localizableResourcesInJar)
    }
    layout.moduleJars.put(relativeJarPath, moduleName)
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
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  void withModuleLibrary(String libraryName, String moduleName, String relativeOutputPath) {
    layout.includedModuleLibraries << new ModuleLibraryData(moduleName: moduleName, libraryName: libraryName,
                                                            relativeOutputPath: relativeOutputPath)
  }

  /**
   * Exclude the specified files when {@code moduleName} is packed into JAR file.
   * <strong>This is a temporary method added to keep layout of some old plugins. If some files from a module shouldn't be included into the
   * module JAR it's strongly recommended to move these files outside of the module source roots.</strong>
   * @param excludedPattern Ant-like pattern describing files to be excluded (relatively to the module output root); e.g. {@code "foo/**"}
   * to exclude 'foo' directory
   */
  void excludeFromModule(String moduleName, String excludedPattern) {
    layout.moduleExcludes.put(moduleName, excludedPattern)
  }

  /**
   * Include an artifact output to the plugin distribution.
   * @param artifactName name of the project configuration  
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  void withArtifact(String artifactName, String relativeOutputPath) {
    layout.includedArtifacts.put(artifactName, relativeOutputPath)
  }
}
