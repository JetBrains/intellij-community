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

/**
 * @author nik
 */
abstract class WindowsDistributionCustomizer {
  /**
   * Path to *.ico file for Windows distribution
   */
  String icoPath

  /**
   * If {@code true} *.bat files (productName.bat and inspect.bat) will be included into the distribution
   */
  boolean includeBatchLaunchers = true

  boolean bundleJre = true
  boolean buildZipWithBundledOracleJre = false

  /**
   * If {@code true} Windows Installer will associate *.ipr files with the IDE in Registry
   */
  boolean associateIpr = true

  /**
   * Path to a directory containing images for installer: logo.bmp, headerlogo.bmp, install.ico, uninstall.ico
   */
  String installerImagesPath

  /**
   * List of file extensions (starting with dot) which installer will suggest to associate with the product
   */
  List<String> fileAssociations = []

  /**
   * Name of the root directory in Windows .zip archive
   */
  abstract String rootDirectoryName(String buildNumber)

  void copyAdditionalFiles(BuildContext context, String targetDirectory) {}

  String uninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    return null
  }
}
