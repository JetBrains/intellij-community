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
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture

/**
 * @author nik
 */
@CompileStatic
class BundledJreManager {
  private final BuildContext buildContext
  String baseDirectoryForJdk

  BundledJreManager(BuildContext buildContext, String communityHome) {
    this.buildContext = buildContext
    this.baseDirectoryForJdk = "$communityHome/../../prebuilts/studio/jdk"
  }

  /**
   * Extract JRE for Linux distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String findLinuxJdk() {
    return "$baseDirectoryForJdk/linux"
  }

  /**
   * Extract JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String findWinJdk(JvmArchitecture arch) {
    return arch == JvmArchitecture.x32 ? "$baseDirectoryForJdk/win32" : "$baseDirectoryForJdk/win64"
  }

  /**
   * Extract JRE for Mac distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String findMacJdk() {
    return "$baseDirectoryForJdk/mac"
  }

  String archiveNameJre(BuildContext buildContext) {
    return "foobar"
  }
}