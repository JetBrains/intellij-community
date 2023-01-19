// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Path

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to support scrambling the product
 * JAR files.
 */
interface ScrambleTool {
  /**
   * @return list of modules used by the tool which need to be compiled before {@link #scramble} method is invoked
   */
  val additionalModulesToCompile: List<String>

  /**
   * Scramble [mainJarName] in "[BuildPaths.distAllDir]/lib" directory
   */
  suspend fun scramble(mainJarName: String, context: BuildContext)

  suspend fun scramblePlugin(context: BuildContext, pluginLayout: PluginLayout, targetDir: Path, additionalPluginsDir: Path)

  /**
   * @return list of names of JAR files which cannot be included into the product 'lib' directory in plain form
   */
  val namesOfJarsRequiredToBeScrambled: List<String>

  /**
   * Returns list of module names which cannot be included into the product without scrambling.
   */
  val namesOfModulesRequiredToBeScrambled: List<String>
}