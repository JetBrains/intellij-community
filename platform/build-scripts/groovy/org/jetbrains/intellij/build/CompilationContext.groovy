// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.JpsCompilationData
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

@CompileStatic
interface CompilationContext {
  AntBuilder getAnt()
  GradleRunner getGradle()
  BuildOptions getOptions()
  BuildMessages getMessages()
  BuildPaths getPaths()
  JpsProject getProject()
  JpsModel getProjectModel()

  JpsCompilationData getCompilationData()

  JpsModule findRequiredModule(String name)

  JpsModule findModule(String name)

  /**
   * If module {@code newName} was renamed returns its old name and {@code null} otherwise. This method can be used to temporary keep names
   * of directories and JARs in the product distributions after renaming modules.
   */
  String getOldModuleName(String newName)

  String getModuleOutputPath(JpsModule module)

  String getModuleTestsOutputPath(JpsModule module)

  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests)

  // "Was" added due to Groovy bug (compilation error - cannot find method with same name but different parameter type)
  void notifyArtifactWasBuilt(Path artifactPath)

  void notifyArtifactBuilt(String artifactPath)
}
