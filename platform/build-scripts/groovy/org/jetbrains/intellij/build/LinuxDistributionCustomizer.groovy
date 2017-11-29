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
 * @author nik
 */
@CompileStatic
abstract class LinuxDistributionCustomizer {
  /**
   * Path to a 128x128 png product icon for Linux distribution
   */
  String iconPngPath

  /**
   * Relative paths to files in Linux distribution which should take 'executable' permissions
   */
  List<String> extraExecutables = []

  /**
   * If {@code true} a separate *-no-jdk.tar.gz artifact without JRE will be produced
   */
  boolean buildTarGzWithoutBundledJre = true

  /**
   * Set both properties if a .snap package should be produced.
   * "snapName" is the name of the package (e.g. "intellij-idea-ultimate", "pycharm-community").
   * "snapDescription" is the plain text description of the package.
   */
  String snapName = null
  String snapDescription = null

  /**
   * Name of the root directory inside linux .tar.gz archive
   */
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "${applicationInfo.productName}-${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to Linux distribution of the product.
   * @param targetDirectory contents of this directory will be packed into .tar.gz archive under {@link #getRootDirectoryName(ApplicationInfoProperties, String)}
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {}
}