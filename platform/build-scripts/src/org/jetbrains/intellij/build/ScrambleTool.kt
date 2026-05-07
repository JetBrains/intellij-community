// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Path

/**
 * Implement this interface and pass the implementation to [ProprietaryBuildTools] constructor to support scrambling the product JAR files.
 */
interface ScrambleTool {
  /**
   * @return list of modules used by the tool which needs to be compiled before [scramble] method is invoked
   */
  val additionalModulesToCompile: List<String>

  /**
   * Scramble [PluginLayout.mainJarName] in "[BuildPaths.distAllDir]/lib" directory.
   *
   * [coScrambleEntries] are extra plugin jars (already laid out under their plugin target dirs)
   * that must be scrambled in the same ZKM run as the platform — see [PluginLayout.scrambleWithPlatform].
   * Cross-plugin/platform references then share one consistent renaming.
   *
   * [classpathDirs] are extra directories whose jar entries must be on the ZKM classpath
   * (for resolving cross-plugin references during trim/obfuscate). Typically the `lib/modules`
   * subdir of every bundled plugin laid out before this run.
   */
  suspend fun scramble(
    platformLayout: PlatformLayout,
    platformContent: List<DistributionFileEntry>,
    coScrambleEntries: List<CoScrambleEntry>,
    classpathDirs: List<Path>,
    context: BuildContext,
  )

  /**
   * One plugin jar to be scrambled together with the platform.
   *
   * - [jarFile] absolute path of the jar (under the plugin's target dir, e.g.
   *   `dist.all/plugins/<dir>/lib/modules/<name>.jar`). The same path is used for the scrambled output.
   * - [pluginLayout] the originating plugin's layout (for descriptor-cache update + reporting).
   * - [pluginDir] the plugin's target dir (`dist.all/plugins/<dir>`) — used to scope the per-plugin
   *   descriptor cache.
   */
  class CoScrambleEntry(
    @JvmField val jarFile: Path,
    @JvmField val pluginLayout: PluginLayout,
    @JvmField val pluginDir: Path,
  )

  suspend fun scramblePlugin(request: PluginScrambleRequest, context: BuildContext)

  /**
   * Returns list of module names which cannot be included in the product without scrambling.
   */
  val namesOfModulesRequiredToBeScrambled: List<String>

  suspend fun validatePlatformLayout(modules: Collection<ModuleItem>, context: BuildContext)
}

@ApiStatus.Internal
class PluginScrambleRequest(
  @JvmField val currentDescriptor: PluginBuildDescriptor,
  @JvmField val laidOutDescriptors: Collection<PluginBuildDescriptor>,
  @JvmField val platformLayout: PlatformLayout,
  @JvmField val platformContent: List<DistributionFileEntry>,
)
