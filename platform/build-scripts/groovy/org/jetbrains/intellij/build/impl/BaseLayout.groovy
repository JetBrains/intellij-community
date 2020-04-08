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


import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.ResourcesGenerator

/**
 * Describes layout of a plugin or the platform JARs in the product distribution
 */
@CompileStatic
abstract class BaseLayout {
  /** JAR name (or path relative to 'lib' directory) to names of modules */
  final MultiMap<String, String> moduleJars = MultiMap.createLinkedSet()
  /** artifact name to relative output path */
  final Map<String, String> includedArtifacts = [:]
  /** list of additional resources which should be included into the distribution */
  final List<ModuleResourceData> resourcePaths = []
  /** module name to entries which should be excluded from its output */
  final MultiMap<String, String> moduleExcludes = MultiMap.createLinked()
  final LinkedHashSet<ProjectLibraryData> includedProjectLibraries = []
  final LinkedHashSet<ModuleLibraryData> includedModuleLibraries = []
  final MultiMap<String, String> excludedModuleLibraries = MultiMap.createLinked()
  /** JAR name -> name of project library which content should be unpacked */
  final MultiMap<String, String> projectLibrariesToUnpack = MultiMap.createLinked()
  /** module name -> name of JAR (or path relative to 'lib' directory) where localizable resources will be placed*/
  protected final Map<String, String> localizableResourcesJars = new LinkedHashMap<>()
  final List<String> modulesWithExcludedModuleLibraries = []
  final List<Pair<ResourcesGenerator, String>> resourceGenerators = []
  /** set of keys in {@link #moduleJars} which are set explicitly, not automatically derived from modules names */
  final Set<String> explicitlySetJarPaths = new LinkedHashSet<>()

  String localizableResourcesJarName(String moduleName) {
    return localizableResourcesJars.get(moduleName)
  }

  static String convertModuleNameToFileName(String moduleName) {
    StringUtil.trimStart(moduleName, "intellij.").replace('.', '-')
  }
}