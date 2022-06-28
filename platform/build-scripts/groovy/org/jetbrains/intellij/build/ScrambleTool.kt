// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to support scrambling the product
 * JAR files.
 */
interface ScrambleTool {
  /**
   * @return list of modules used by the tool which need to be compiled before {@link #scramble} method is invoked
   */
  fun getAdditionalModulesToCompile(): List<String>

  /**
   * Scramble {@code mainJarName} in {@code "$buildContext.paths.distAll/lib"} directory
   */
  fun scramble(mainJarName: String, buildContext: BuildContext)

  @Nullable
  fun scramblePlugin(buildContext: BuildContext, pluginLayout: PluginLayout, targetDir: Path, additionalPluginsDir: Path): ForkJoinTask<*>?

  /**
   * @return list of names of JAR files which cannot be included into the product 'lib' directory in plain form
   */
  fun getNamesOfJarsRequiredToBeScrambled(): List<String>

  /**
   * Returns list of module names which cannot be included into the product without scrambling.
   */
  fun getNamesOfModulesRequiredToBeScrambled(): List<String>
}