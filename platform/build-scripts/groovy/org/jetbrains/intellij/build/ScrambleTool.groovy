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

/**
 * Implement this interfaces and pass the implementation to {@link ProprietaryBuildTools} constructor to support scrambling the product
 * JAR files.
 *
 * @author nik
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
   * @return list of names of JAR files which cannot be included into the product 'lib' directory in plain form
   */
  List<String> getNamesOfJarsRequiredToBeScrambled()

  /**
   * Returns list of module names which cannot be included into the product without scrambling.
   */
  List<String> getNamesOfModulesRequiredToBeScrambled()
}