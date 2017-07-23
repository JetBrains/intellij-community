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

/**
 * All paths are absolute and use '/' as a separator
 *
 * @author nik
 */
@CompileStatic
abstract class BuildPaths {
  /**
   * Path to a directory where idea/community Git repository is checked out
   */
  String communityHome

  /**
   * Path to a base directory of the project which will be compiled
   */
  String projectHome

  /**
   * Path to a directory where build script will store temporary and resulting files
   */
  String buildOutputRoot

  /**
   * Path to a directory where resulting artifacts will be placed
   */
  String artifacts

  /**
   * Path to a directory containing distribution files ('bin', 'lib', 'plugins' directories) common for all operating systems
   */
  String distAll

  /**
   * Path to a directory where temporary files required for a particular build step can be stored
   */
  String temp

  /**
   * Path to a directory containing JDK (currently Java 8) which is used to compile the project
   */
  String jdkHome
  
  /**
   * Path to a directory containing Kotlin plugin with compiler which is used to compile the project
   */
  String kotlinHome
}
