/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.intellij.build.impl.BuildTasksImpl

/**
 * @author nik
 */
@CompileStatic
abstract class BuildTasks {
  /**
   * Builds sources.zip archive containing the project source files keeping the original layout
   */
  abstract void zipProjectSources()

  /**
   * Builds archive containing production source roots of the project modules
   */
  abstract void zipSourcesOfModules(Collection<String> modules, String targetFilePath)

  /**
   * Updates search/searchableOptions.xml file in {@code targetModuleName} module output directory
   * <br>
   * todo[nik] this is temporary solution until code from layouts.gant files moved to the new builders. After that this method will
   * be called inside {@link #buildDistributions()}
   */
  abstract void buildSearchableOptions(String targetModuleName, List<String> modulesToIndex, List<String> pathsToLicenses)

  /**
   * Creates a copy of *ApplicationInfo.xml file with substituted __BUILD_NUMBER__ and __BUILD_DATE__ placeholders
   * <br>
   * todo[nik] this is temporary solution until code from layouts.gant files moved to the new builders. After that this method will
   * be called inside {@link #buildDistributions()}
   * @return path to the copied file
   */
  abstract File patchApplicationInfo()

  /**
   * Creates distribution for all operating systems from JAR files located at {@link BuildPaths#distAll}
   */
  abstract void buildDistributions()

  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to {@link ProductProperties#productLayout}, and creating distributions and installers for all OS.
   */
  abstract void compileModulesAndBuildDistributions()

  abstract void compileProjectAndTests(List<String> includingTestsInModules)

  abstract void compileModules(List<String> moduleNames, List<String> includingTestsInModules = [])

  abstract void buildUpdaterJar()

  static BuildTasks create(BuildContext context) {
    return new BuildTasksImpl(context)
  }
}