// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.Strings
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
/**
 * Describes layout of a plugin or the platform JARs in the product distribution
 */
@CompileStatic
abstract class BaseLayout {
  public static final String APP_JAR = "app.jar"

  /** JAR name (or path relative to 'lib' directory) to names of modules */
  final MultiMap<String, String> moduleJars = MultiMap.createLinkedSet()
  /** artifact name to relative output path */
  final Map<String, String> includedArtifacts = new LinkedHashMap<>()
  /** list of additional resources which should be included into the distribution */
  final List<ModuleResourceData> resourcePaths = new ArrayList<>()
  /** module name to entries which should be excluded from its output */
  final MultiMap<String, String> moduleExcludes = MultiMap.createLinked()
  @ApiStatus.Internal
  public final ObjectOpenHashSet<ProjectLibraryData> includedProjectLibraries = new ObjectOpenHashSet<>()
  final Set<ModuleLibraryData> includedModuleLibraries = new LinkedHashSet<>()
  /** module name to name of the module library */
  final MultiMap<String, String> excludedModuleLibraries = MultiMap.createLinked()
  /** JAR name -> name of project library which content should be unpacked */
  final MultiMap<String, String> projectLibrariesToUnpack = MultiMap.createLinked()
  final List<String> modulesWithExcludedModuleLibraries = []
  /** set of keys in {@link #moduleJars} which are set explicitly, not automatically derived from modules names */
  final Set<String> explicitlySetJarPaths = new LinkedHashSet<>()

  private final Map<String, String> moduleNameToJarPath = new HashMap<>()

  final Collection<String> getIncludedModuleNames() {
    return moduleJars.values()
  }

  final Set<Map.Entry<String, Collection<String>>> getJarToIncludedModuleNames() {
    return moduleJars.entrySet()
  }

  static String convertModuleNameToFileName(String moduleName) {
    return Strings.trimStart(moduleName, "intellij.").replace('.' as char, '-' as char)
  }

  final void withModule(String moduleName, String relativeJarPath) {
    checkAndAssociateModuleNameWithJarPath(moduleName, relativeJarPath)

    moduleJars.putValue(relativeJarPath, moduleName)
    explicitlySetJarPaths.add(relativeJarPath)
  }

  private void checkAndAssociateModuleNameWithJarPath(String moduleName, String relativeJarPath) {
    String previousJarPath = moduleNameToJarPath.putIfAbsent(moduleName, relativeJarPath)
    if (previousJarPath != null && moduleName != "intellij.maven.artifactResolver.common") {
      if (previousJarPath == relativeJarPath) {
        // already added
        return
      }

      // allow to put module to several JARs if JAR located in another dir
      // (e.g. intellij.spring.customNs packed into main JAR and customNs/customNs.jar)
      if (!previousJarPath.contains("/") && !relativeJarPath.contains("/")) {
        throw new IllegalStateException(
          "$moduleName cannot be packed into $relativeJarPath because it is already configured to be packed into $previousJarPath")
      }
    }
  }

  void withModule(@NotNull String moduleName) {
    withModuleImpl(moduleName, convertModuleNameToFileName(moduleName) + ".jar")
  }

  protected final void withModuleImpl(String moduleName, String jarPath) {
    checkAndAssociateModuleNameWithJarPath(moduleName, jarPath)
    moduleJars.putValue(jarPath, moduleName)
  }

  final void withProjectLibraryUnpackedIntoJar(String libraryName, String jarName) {
    projectLibrariesToUnpack.putValue(jarName, libraryName)
  }

  void excludeFromModule(String moduleName, String excludedPattern) {
    moduleExcludes.putValue(moduleName, excludedPattern)
  }
}