// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  final List<Pair<ResourcesGenerator, String>> resourceGenerators = new ArrayList<>()
  /** set of keys in {@link #moduleJars} which are set explicitly, not automatically derived from modules names */
  final Set<String> explicitlySetJarPaths = new LinkedHashSet<>()

  String localizableResourcesJarName(String moduleName) {
    return localizableResourcesJars.get(moduleName)
  }

  static String convertModuleNameToFileName(String moduleName) {
    StringUtil.trimStart(moduleName, "intellij.").replace('.', '-')
  }
}