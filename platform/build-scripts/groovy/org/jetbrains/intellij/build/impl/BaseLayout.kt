// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.containers.MultiMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

/**
 * Describes layout of a plugin or the platform JARs in the product distribution
 */
open class BaseLayout {
  companion object {
    const val APP_JAR = "app.jar"
  }

  /** JAR name (or path relative to 'lib' directory) to names of modules */
  val moduleJars: MultiMap<String, String> = MultiMap.createLinkedSet()
  /** artifact name to relative output path */
  val includedArtifacts: MutableMap<String, String> = LinkedHashMap()
  /** list of additional resources which should be included into the distribution */
  val resourcePaths: MutableList<ModuleResourceData> = mutableListOf()
  /** module name to entries which should be excluded from its output */
  val moduleExcludes: MultiMap<String, String> = MultiMap.createLinked()
  @Suppress("SSBasedInspection")
  internal val includedProjectLibraries: ObjectOpenHashSet<ProjectLibraryData> = ObjectOpenHashSet()
  internal val includedModuleLibraries: MutableSet<ModuleLibraryData> = LinkedHashSet()
  /** module name to name of the module library */
  val excludedModuleLibraries: MultiMap<String, String> = MultiMap.createLinked()
  /** JAR name -> name of project library which content should be unpacked */
  val projectLibrariesToUnpack: MultiMap<String, String> = MultiMap.createLinked()
  val modulesWithExcludedModuleLibraries: MutableList<String> = mutableListOf()
  /** set of keys in {@link #moduleJars} which are set explicitly, not automatically derived from modules names */
  val explicitlySetJarPaths: MutableSet<String> = LinkedHashSet()

  private val moduleNameToJarPath: MutableMap<String, String> = LinkedHashMap()

  fun getIncludedModuleNames(): Collection<String> = moduleJars.values()

  fun getJarToIncludedModuleNames(): Set<Map.Entry<String, Collection<String>>> = moduleJars.entrySet()

  open fun withModule(moduleName: String, relativeJarPath: String) {
    checkAndAssociateModuleNameWithJarPath(moduleName, relativeJarPath)

    moduleJars.putValue(relativeJarPath, moduleName)
    explicitlySetJarPaths.add(relativeJarPath)
  }

  private fun checkAndAssociateModuleNameWithJarPath(moduleName: String, relativeJarPath: String) {
    val previousJarPath = moduleNameToJarPath.putIfAbsent(moduleName, relativeJarPath)
    if (previousJarPath != null && moduleName != "intellij.maven.artifactResolver.common") {
      if (previousJarPath == relativeJarPath) {
        // already added
        return
      }

      // allow to put module to several JARs if JAR located in another dir
      // (e.g. intellij.spring.customNs packed into main JAR and customNs/customNs.jar)
      if (!previousJarPath.contains("/") && !relativeJarPath.contains('/')) {
        error("$moduleName cannot be packed into $relativeJarPath because it is already configured to be packed into $previousJarPath")
      }
    }
  }

  open fun withModule(moduleName: String) {
    withModuleImpl(moduleName, "${convertModuleNameToFileName(moduleName)}.jar")
  }

  protected fun withModuleImpl(moduleName: String, jarPath: String) {
    checkAndAssociateModuleNameWithJarPath(moduleName, jarPath)
    moduleJars.putValue(jarPath, moduleName)
  }

  fun withProjectLibraryUnpackedIntoJar(libraryName: String, jarName: String) {
    projectLibrariesToUnpack.putValue(jarName, libraryName)
  }

  fun excludeFromModule(moduleName: String, excludedPattern: String) {
    moduleExcludes.putValue(moduleName, excludedPattern)
  }
}

internal fun convertModuleNameToFileName(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-')

internal data class ModuleLibraryData(
  val moduleName: String,
  val libraryName: String,
  val relativeOutputPath: String,
)