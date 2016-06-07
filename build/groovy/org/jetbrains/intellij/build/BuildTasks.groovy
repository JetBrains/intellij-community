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

import org.jetbrains.intellij.build.impl.BuildTasksImpl

/**
 * @author nik
 */
abstract class BuildTasks {
  /**
   * Build sources.zip archive containing the project source files keeping the original layout
   */
  abstract void zipProjectSources()

  /**
   * Build archive containing production source roots of the project modules
   */
  abstract void zipSourcesOfModules(Collection<String> modules, String targetFilePath)

  /**
   * Update search/searchableOptions.xml file in {@code targetModuleName} module output directory
   */
  abstract void buildSearchableOptions(String targetModuleName, List<String> modulesToIndex, List<String> pathsToLicenses)

  /**
   * Create a copy of *ApplicationInfo.xml file with substituted __BUILD_NUMBER__ and __BUILD_DATE__ placeholders
   * @return path to the copied file
   */
  abstract File patchApplicationInfo()

  /**
   * Create distribution for all operating system from JAR files located at {@link BuildPaths#distAll}
   */
  abstract void buildDistributions()

  abstract void cleanOutput()

  abstract void compileProjectAndTests(List<String> includingTestsInModules = [])

  public static BuildTasks create(BuildContext context) {
    return new BuildTasksImpl(context)
  }
}