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
 * @author nik
 */
@CompileStatic
abstract class WindowsDistributionCustomizer {
  /**
   * Path to 256x256 *.ico file for Windows distribution
   */
  String icoPath

  /**
   * If {@code true} *.bat files (productName.bat and inspect.bat) will be included into the distribution
   */
  boolean includeBatchLaunchers = true

  /**
   * Specify bitness of bundled JRE. If {@code null} no JRE will be bundled
   */
  JvmArchitecture bundledJreArchitecture = JvmArchitecture.x32

  /**
   * If {@code true} a Zip archive containing the installation with bundled Oracle JRE will be produced
   */
  boolean buildZipWithBundledOracleJre = false

  /**
   * If {@code true} a Zip archive containing the installation with bundled JetBrains RE will be produced
   */
  boolean buildZipArchive = true

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
   * Paths to files which will be used to overwrite the standard *.nsi files
   */
  List<String> customNsiConfigurationFiles = []

  /**
   * Name of the root directory in Windows .zip archive
   */
  String rootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "" }

  /**
   * Override this method to copy additional files to Windows distribution of the product.
   * @param targetDirectory contents of this directory will be packed into zip archive and exe installer, so when the product is installed
   * it'll be placed under its root directory.
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {}

  /**
   * The returned name will be shown in Windows Installer and used in Registry keys
   */
  String fullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { applicationInfo.productName }

  /**
   * The returned name will be used to create links on Desktop
   */
  String fullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { applicationInfo.shortCompanyName + " " + fullNameIncludingEdition(applicationInfo) }

  String uninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    return null
  }
}
