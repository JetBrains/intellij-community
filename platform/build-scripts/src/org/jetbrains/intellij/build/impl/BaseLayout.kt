// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.containers.MultiMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.*
import org.jetbrains.annotations.TestOnly
import java.lang.IllegalStateException
import java.lang.StackWalker.Option
import kotlin.collections.LinkedHashSet
import kotlin.streams.asSequence

const val APP_JAR: String = "app.jar"
const val PRODUCT_JAR: String = "product.jar"
const val TEST_FRAMEWORK_JAR: String = "testFramework.jar"

/**
 * Describes layout of a plugin or the platform JARs in the product distribution
 */
sealed class BaseLayout {
  // one module can be packed into several JARs; that's why we have map "jar to modules" and not "module to jar"
  private val _includedModules = LinkedHashSet<ModuleItem>()

  val includedModules: Collection<ModuleItem>
    get() = _includedModules

  /** artifact name to a relative output path */
  @JvmField
  internal var includedArtifacts: PersistentMap<String, String> = persistentMapOf()

  /** list of additional resources which should be included in the distribution */
  @JvmField
  internal var resourcePaths: PersistentList<ModuleResourceData> = persistentListOf()

  /** module name to entries which should be excluded from its output */
  var moduleExcludes: PersistentMap<String, MutableList<String>> = persistentMapOf()
    private set

  @Suppress("SSBasedInspection")
  @JvmField
  @PublishedApi
  internal val includedProjectLibraries: ObjectOpenHashSet<ProjectLibraryData> = ObjectOpenHashSet()
  val includedModuleLibraries: MutableSet<ModuleLibraryData> = LinkedHashSet()

  /** module name to name of the module library */
  val excludedModuleLibraries: MultiMap<String, String> = MultiMap.createLinked()

  val modulesWithExcludedModuleLibraries: MutableList<String> = mutableListOf()

  fun hasLibrary(name: String): Boolean = includedProjectLibraries.any { it.libraryName == name }

  @TestOnly
  fun includedProjectLibraryNames(): Sequence<String> = includedProjectLibraries.asSequence().map { it.libraryName }

  fun filteredIncludedModuleNames(excludedRelativeJarPath: String): Sequence<String> {
    return _includedModules.asSequence().filter { it.relativeOutputFile != excludedRelativeJarPath }.map { it.moduleName }
  }

  fun withModules(items: Collection<ModuleItem>) {
    for (item in items) {
      checkNotExists(item)
    }
    _includedModules.addAll(items)
  }

  private fun checkNotExists(item: ModuleItem) {
    val existing = _includedModules.firstOrNull { it.moduleName == item.moduleName } ?: return
    // allow putting module to several JARs if JAR located in another dir
    // (e.g. intellij.spring.customNs packed into main JAR and customNs/customNs.jar)
    if (existing.relativeOutputFile != item.relativeOutputFile &&
        (existing.relativeOutputFile.contains('/') || item.relativeOutputFile.contains('/'))) {
      return
    }

    if (item.moduleName.startsWith("intellij.maven.artifactResolver.")) {
      return
    }

    throw IllegalStateException(
      "Module ${item.moduleName} is already configured to be included in the layout. " +
      "Please make sure the module name is not duplicated." +
      "\n  The existing: $existing" +
      "\n  The new: $item"
    )
  }

  abstract fun withModule(moduleName: String)

  fun withModules(names: Iterable<String>) {
    names.forEach(::withModule)
  }

  fun withModule(moduleName: String, relativeJarPath: String) {
    require(!moduleName.isEmpty()) {
      "Module name must be not empty"
    }

    val stackTrace = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).walk { stream ->
      stream.use {
        stream.asSequence()
          .dropWhile {
            val declaringClass = it.declaringClass.name
            // startsWith - spec class
            declaringClass.startsWith("org.jetbrains.intellij.build.impl.BaseLayout") ||
            // startsWith - `Companion` object
            declaringClass.startsWith("org.jetbrains.intellij.build.impl.PluginLayout")
          }
          .take(3)
          .joinToString(separator = "\n    ")
      }
    }

    val item = ModuleItem(moduleName = moduleName, relativeOutputFile = relativeJarPath, reason = "withModule at \n    $stackTrace")
    checkNotExists(item)
    _includedModules.add(item)
  }

  fun withProjectLibrary(libraryName: String, jarName: String, reason: String? = null) {
    includedProjectLibraries.add(ProjectLibraryData(libraryName = libraryName,
                                                    packMode = LibraryPackMode.STANDALONE_MERGED,
                                                    outPath = jarName,
                                                    reason = reason))
  }

  fun withProjectLibraries(libraryNames: Collection<String>, jarName: String, reason: String? = null) {
    for (libraryName in libraryNames) {
      includedProjectLibraries.add(ProjectLibraryData(libraryName = libraryName,
                                                      packMode = LibraryPackMode.STANDALONE_MERGED,
                                                      outPath = jarName,
                                                      reason = reason))
    }
  }

  fun excludeFromModule(moduleName: String, excludedPattern: String) {
    moduleExcludes = moduleExcludes.mutate {
      it.computeIfAbsent(moduleName) { mutableListOf() }.add(excludedPattern)
    }
  }

  fun excludeFromModule(moduleName: String, excludedPatterns: List<String>) {
    moduleExcludes = moduleExcludes.mutate {
      it.computeIfAbsent(moduleName) { mutableListOf() }.addAll(excludedPatterns)
    }
  }

  fun withProjectLibrary(libraryName: String) {
    includedProjectLibraries.add(ProjectLibraryData(libraryName = libraryName, packMode = LibraryPackMode.MERGED))
  }

  fun withProjectLibrary(libraryName: String, packMode: LibraryPackMode) {
    includedProjectLibraries.add(ProjectLibraryData(libraryName = libraryName, packMode = packMode))
  }

  /**
   * Include the module library to the plugin distribution. Please note that it makes sense to call this method only
   * for additional modules which aren't copied directly to the 'lib' directory of the plugin distribution, because for ordinary modules
   * their module libraries are included in the layout automatically.
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  fun withModuleLibrary(libraryName: String, moduleName: String, relativeOutputPath: String) {
    includedModuleLibraries.add(ModuleLibraryData(
      moduleName = moduleName,
      libraryName = libraryName,
      relativeOutputPath = relativeOutputPath,
    ))
  }

  /**
   * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
   * @param relativeOutputPath target path relative to the plugin root directory
   */
  fun withResourceFromModule(moduleName: String, resourcePath: String, relativeOutputPath: String) {
    resourcePaths = resourcePaths.add(ModuleResourceData(moduleName = moduleName,
                                                         resourcePath = resourcePath,
                                                         relativeOutputPath = relativeOutputPath,
                                                         packToZip = false))
  }
}

data class ModuleLibraryData(
  @JvmField val moduleName: String,
  @JvmField val libraryName: String,
  @JvmField val relativeOutputPath: String = "",
)

class ModuleItem(
  @JvmField val moduleName: String,
  // for one module, maybe several JARs - that's why `relativeOutputPath` is included into hash code
  @JvmField val relativeOutputFile: String,
  @JvmField val reason: String?,
) {
  init {
    require(!moduleName.isEmpty()) {
      "Module name must be not empty"
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is ModuleItem) {
      return false
    }

    if (moduleName != other.moduleName) {
      return false
    }
    return relativeOutputFile == other.relativeOutputFile
  }

  override fun hashCode(): Int {
    var result = moduleName.hashCode()
    result = 31 * result + relativeOutputFile.hashCode()
    return result
  }

  override fun toString(): String = "ModuleItem(moduleName=$moduleName, relativeOutputFile=$relativeOutputFile, reason=$reason)"
}