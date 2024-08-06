// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import java.lang.StackWalker.Option
import kotlin.streams.asSequence

typealias LayoutPatcher = suspend (ModuleOutputPatcher, PlatformLayout, BuildContext) -> Unit

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

  internal var patchers: PersistentList<LayoutPatcher> = persistentListOf()
    private set

  fun withPatch(patcher: LayoutPatcher) {
    patchers = patchers.add(patcher)
  }

  fun withPatch(patcher: suspend (ModuleOutputPatcher, BuildContext) -> Unit) {
    patchers = patchers.add { moduleOutputPatcher, _, buildContext -> patcher(moduleOutputPatcher, buildContext) }
  }

  fun hasLibrary(name: String): Boolean = includedProjectLibraries.any { it.libraryName == name }

  fun findProjectLibrary(name: String): ProjectLibraryData? = includedProjectLibraries.firstOrNull { it.libraryName == name }

  @TestOnly
  fun includedProjectLibraryNames(): Sequence<String> = includedProjectLibraries.asSequence().map { it.libraryName }

  fun filteredIncludedModuleNames(excludedRelativeJarPath: String, includeFromSubdirectories: Boolean = true): Sequence<String> {
    return _includedModules.asSequence().filter { 
      it.relativeOutputFile != excludedRelativeJarPath && (includeFromSubdirectories || !it.relativeOutputFile.contains('/')) 
    }.map { it.moduleName }
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
    includedProjectLibraries.add(
      ProjectLibraryData(libraryName = libraryName, packMode = LibraryPackMode.MERGED, reason = "withProjectLibrary")
    )
  }

  internal fun withProjectLibraries(libraryNames: List<String>, outPath: String? = null) {
    for (libraryName in libraryNames) {
      includedProjectLibraries.add(
        ProjectLibraryData(
          libraryName = libraryName,
          packMode = LibraryPackMode.MERGED,
          reason = "withProjectLibrary",
          outPath = outPath,
        )
      )
    }
  }

  fun withProjectLibraries(libraryNames: Iterable<String>) {
    libraryNames.forEach(::withProjectLibrary)
  }

  fun withProjectLibrary(libraryName: String, packMode: LibraryPackMode) {
    includedProjectLibraries.add(ProjectLibraryData(libraryName = libraryName, packMode = packMode, reason = "withProjectLibrary"))
  }

  /**
   * Include the module library to the plugin distribution. Please note that it makes sense to call this method only
   * for additional modules which aren't copied directly to the 'lib' directory of the plugin distribution, because for ordinary modules,
   * their module libraries are included in the layout automatically.
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  fun withModuleLibrary(libraryName: String, moduleName: String, relativeOutputPath: String, extraCopy: Boolean = false) {
    includedModuleLibraries.add(ModuleLibraryData(
      moduleName = moduleName,
      libraryName = libraryName,
      relativeOutputPath = relativeOutputPath,
      extraCopy = extraCopy
    ))
  }

  /**
   * @param resourcePath path to resource file or directory relative to `moduleName` module content root
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
  // set to true to have a library both packed to a plugin and copied to the plugin as additional JAR
  @JvmField val extraCopy: Boolean = false
)

class ModuleItem(
  @JvmField val moduleName: String,
  // for one module, maybe several JARs - that's why `relativeOutputPath` is included in hash code
  @JvmField val relativeOutputFile: String,
  @JvmField val reason: String?,
) {
  init {
    require(!moduleName.isEmpty()) {
      "Module name must be not empty"
    }
  }

  override fun equals(other: Any?): Boolean {
    return this === other || other is ModuleItem && moduleName == other.moduleName && relativeOutputFile == other.relativeOutputFile
  }

  override fun hashCode(): Int = 31 * moduleName.hashCode() + relativeOutputFile.hashCode()

  override fun toString(): String = "ModuleItem(moduleName=$moduleName, relativeOutputFile=$relativeOutputFile, reason=$reason)"
}
