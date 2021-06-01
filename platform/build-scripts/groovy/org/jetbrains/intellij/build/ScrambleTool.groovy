// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

import java.nio.file.Path

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to support scrambling the product
 * JAR files.
 */
@CompileStatic
interface ScrambleTool {
  /**
   * @return list of modules used by the tool which need to be compiled before {@link #scramble} method is invoked
   */
  List<String> getAdditionalModulesToCompile()

  /**
   * Scramble {@code mainJarName} in {@code "$buildContext.paths.distAll/lib"} directory
   */
  void scramble(String mainJarName, BuildContext buildContext)

  /**
   * Scramble plugin's {@code jarsToScramble} jars
   * Plugin's zkm script template is expected to be in
   *  if (pluginLayout.zkmScriptStub) is set, then
   *     $buildContext.paths.projectHome/${pluginLayout.zkmScriptStub}"
   *  else
   *     "$buildContext.paths.projectHome/plugins/{@code pluginName}/build/script.zkm.stub"
   */
  void scramblePlugin(BuildContext buildContext, PluginLayout pluginLayout, Path targetDir, Path pluginsDir)

  /**
   * @return list of names of JAR files which cannot be included into the product 'lib' directory in plain form
   */
  List<String> getNamesOfJarsRequiredToBeScrambled()

  /**
   * Returns list of module names which cannot be included into the product without scrambling.
   */
  List<String> getNamesOfModulesRequiredToBeScrambled()
}