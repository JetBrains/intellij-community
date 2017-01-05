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
/**
 * Describes layout of a plugin or the platform JARs in the product distribution
 *
 * @author nik
 */
abstract class BaseLayout {
  /** JAR name (or path relative to 'lib' directory) to module name */
  final MultiValuesMap<String, String> moduleJars = new MultiValuesMap<>(true)
  final List<ModuleResourceData> resourcePaths = []
  /** module name to entries which should be excluded from its output */
  final MultiValuesMap<String, String> moduleExcludes = new MultiValuesMap<>(true)
  final List<String> includedProjectLibraries = []
  final List<ModuleLibraryData> includedModuleLibraries = []
  /** JAR name -> name of project library which content should be unpacked */
  final MultiValuesMap<String, String> projectLibrariesToUnpack = new MultiValuesMap<>()
  protected final Set<String> modulesWithLocalizableResourcesInCommonJar = new LinkedHashSet<>()
  final List<String> modulesWithExcludedModuleLibraries = []

  boolean packLocalizableResourcesInCommonJar(String moduleName) {
    return modulesWithLocalizableResourcesInCommonJar.contains(moduleName)
  }
}