// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.DependenciesProperties
import org.jetbrains.intellij.build.impl.JpsCompilationData
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

@CompileStatic
interface CompilationContext {
  AntBuilder getAnt()
  BuildOptions getOptions()
  BuildMessages getMessages()
  BuildPaths getPaths()
  JpsProject getProject()
  JpsModel getProjectModel()
  DependenciesProperties getDependenciesProperties()
  BundledRuntime getBundledRuntime()

  JpsCompilationData getCompilationData()

  /**
   * @return directory with compiled project classes, url attribute value of output tag from .idea/misc.xml by default
   */
  File getProjectOutputDirectory()

  JpsModule findRequiredModule(@NotNull String name)

  JpsModule findModule(@NotNull String name)

  /**
   * If module {@code newName} was renamed returns its old name and {@code null} otherwise. This method can be used to temporary keep names
   * of directories and JARs in the product distributions after renaming modules.
   */
  String getOldModuleName(String newName)

  Path getModuleOutputDir(JpsModule module)

  String getModuleTestsOutputPath(JpsModule module)

  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests)

  // "Was" added due to Groovy bug (compilation error - cannot find method with same name but different parameter type)
  void notifyArtifactWasBuilt(Path artifactPath)

  /**
   * @deprecated Use {@link #notifyArtifactWasBuilt(java.nio.file.Path)}
   */
  @Deprecated
  void notifyArtifactBuilt(String artifactPath)
}
