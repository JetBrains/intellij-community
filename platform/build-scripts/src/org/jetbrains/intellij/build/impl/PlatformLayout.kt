// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR
import java.nio.file.Path

/**
 * How production packaging and a complete dev build place the files of one [PlatformDistFileDeclaration].
 * The dev distribution does not read this half; its component places the files from the layout asset spec.
 */
@ApiStatus.Internal
sealed interface PlatformDistFilePlacement {
  /**
   * The files are registered as [DistFile]s. The cross-platform zip and the mac zip take a dist file from its source,
   * and the content report lists it.
   */
  class RegisteredDistFiles(@JvmField val distFiles: (BuildContext) -> List<DistFile>) : PlatformDistFilePlacement

  /**
   * The files are copied into the OS-specific distribution directory, like the `bin` files. The cross-platform zip
   * does not take them, and the mac signing pass covers them through the executable patterns. [copy] gets the
   * distribution directory and returns the files it wrote.
   */
  class OsSpecificFiles(@JvmField val copy: (Path, BuildContext) -> List<Path>) : PlatformDistFilePlacement
}

/**
 * The files a downloaded dependency contributes to the distribution for one target platform.
 *
 * [layoutAssetSpec] is the data the dev distribution packs from: the archive as a Bazel label, and each archive path
 * with its distribution destination. [placement] places the same files for production packaging and for a complete
 * dev build. Both halves describe the same files, so a change to one is a change to the other.
 */
@ApiStatus.Internal
class PlatformDistFileDeclaration(
  @JvmField val layoutAssetSpec: DevPluginLayoutAssetSpec,
  @JvmField val placement: PlatformDistFilePlacement,
)

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 *
 * It includes all modules specified in [org.jetbrains.intellij.build.productLayout.ProductModulesLayout] and the module libraries they depend on.
 *
 * Project libraries are never added implicitly - only the ones declared by [BaseLayoutSpec.withProjectLibrary] are packed.
 * A project library that a plugin module references is not packed for the plugin; the platform or the plugin layout declares it.
 */
class PlatformLayout(@JvmField val descriptorCacheContainer: DescriptorCacheContainer = DescriptorCacheContainer()) : BaseLayout() {
  private val productModuleOutputFileOverrides: MutableMap<String, String> = HashMap()

  override fun getRelativeJarPath(moduleName: String): String = APP_BACKEND_JAR

  fun withProductModuleOutputFile(moduleName: String, relativeOutputFile: String) {
    require(!moduleName.isEmpty()) {
      "Module name must be not empty"
    }
    require(!relativeOutputFile.isEmpty()) {
      "Relative output file must be not empty"
    }
    require(!relativeOutputFile.startsWith("/") && relativeOutputFile.endsWith(".jar")) {
      "Relative output file for $moduleName must be a relative JAR path: $relativeOutputFile"
    }

    val previous = productModuleOutputFileOverrides.get(moduleName)
    check(previous == null || previous == relativeOutputFile) {
      "Product module output file for $moduleName is already set to $previous, cannot set to $relativeOutputFile"
    }
    productModuleOutputFileOverrides.put(moduleName, relativeOutputFile)
  }

  internal fun getProductModuleOutputFile(moduleName: String): String? = productModuleOutputFileOverrides.get(moduleName)

  /** The declared dist files per target platform, in declaration order. Keyed by the OS and the architecture. */
  @ApiStatus.Internal
  var distFileDeclarations: PersistentMap<Pair<OsFamily, JvmArchitecture>, PersistentList<PlatformDistFileDeclaration>> = persistentMapOf()
    private set

  /**
   * Declares the files a downloaded dependency contributes to the distribution on [os] and [arch].
   *
   * The plugin counterpart is `PluginLayoutSpec.withGeneratedPlatformResources` with a layout asset spec. The dev
   * distribution packs the files [layoutAssetSpec] names out of the archive it names. Production packaging and a
   * complete dev build register the [DistFile]s [distFiles] returns; the build calls it once per declaration.
   */
  fun withDistFiles(
    os: OsFamily,
    arch: JvmArchitecture,
    layoutAssetSpec: DevPluginLayoutAssetSpec,
    distFiles: (BuildContext) -> List<DistFile>,
  ) {
    declareDistFiles(os, arch, layoutAssetSpec, PlatformDistFilePlacement.RegisteredDistFiles(distFiles))
  }

  /**
   * Declares the files a downloaded dependency contributes to the OS-specific distribution directory on [os] and [arch].
   *
   * The dev distribution packs the files [layoutAssetSpec] names, as with [withDistFiles]. Production packaging and a
   * complete dev build call [copy] with the distribution directory where the OS builder copies its `bin` files; [copy]
   * returns the files it wrote, so the build can set their executable bits and keep them out of a stale-file sweep.
   */
  fun withOsSpecificFiles(
    os: OsFamily,
    arch: JvmArchitecture,
    layoutAssetSpec: DevPluginLayoutAssetSpec,
    copy: (Path, BuildContext) -> List<Path>,
  ) {
    declareDistFiles(os, arch, layoutAssetSpec, PlatformDistFilePlacement.OsSpecificFiles(copy))
  }

  private fun declareDistFiles(os: OsFamily, arch: JvmArchitecture, layoutAssetSpec: DevPluginLayoutAssetSpec, placement: PlatformDistFilePlacement) {
    require(!layoutAssetSpec.omitted && layoutAssetSpec.assets.isNotEmpty()) {
      "A platform dist file declaration for ${os.osId}/${arch.name} must name its assets"
    }
    val key = os to arch
    val declaration = PlatformDistFileDeclaration(layoutAssetSpec, placement)
    distFileDeclarations = distFileDeclarations.putting(key, (distFileDeclarations.get(key) ?: persistentListOf()).adding(declaration))
  }
}
