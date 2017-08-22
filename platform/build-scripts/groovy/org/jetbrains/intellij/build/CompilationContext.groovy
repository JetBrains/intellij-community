/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.JpsCompilationData
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author nik
 */
@CompileStatic
interface CompilationContext {
  AntBuilder getAnt()
  GradleRunner getGradle()
  BuildOptions getOptions()
  BuildMessages getMessages()
  BuildPaths getPaths()
  JpsProject getProject()
  JpsModel getProjectModel()

  /**
   * @deprecated use {@link CompilationTasks} instead
   */
  JpsGantProjectBuilder getProjectBuilder()

  JpsCompilationData getCompilationData()

  JpsModule findRequiredModule(String name)

  JpsModule findModule(String name)

  String getModuleOutputPath(JpsModule module)

  String getModuleTestsOutputPath(JpsModule module)

  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests)

  void notifyArtifactBuilt(String artifactPath)
}
