// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Path

/**
 * Implement this interfaces and pass the implementation to [ProprietaryBuildTools] constructor to support scrambling the product
 * JAR files.
 */
interface ScrambleTool {
  /**
   * @return list of modules used by the tool which needs to be compiled before [scramble] method is invoked
   */
  val additionalModulesToCompile: List<String>

  /**
   * Scramble [PluginLayout.mainJarName] in "[BuildPaths.distAllDir]/lib" directory
   */
  suspend fun scramble(platform: PlatformLayout, context: BuildContext)

  suspend fun scramblePlugin(pluginLayout: PluginLayout, targetDir: Path, additionalPluginDir: Path, layouts: Collection<PluginLayout>, context: BuildContext)

  /**
   * Returns list of module names which cannot be included in the product without scrambling.
   */
  val namesOfModulesRequiredToBeScrambled: List<String>

  suspend fun validatePlatformLayout(modules: Collection<ModuleItem>, context: BuildContext)
}