// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder

import java.nio.file.Paths

@CompileStatic
abstract class WindowsDistributionCustomizer {
  /**
   * Path to 256x256 *.ico file for Windows distribution
   */
  String icoPath

  /**
   * Path to ico file for EAP builds (if {@code null} {@link #icoPath} will be used)
   */
  String icoPathForEAP = null

  /**
   * If {@code true} *.bat files (productName.bat and inspect.bat) will be included into the distribution
   */
  boolean includeBatchLaunchers = true

  /**
   * If {@code true} a Zip archive containing the installation will be produced
   */
  boolean buildZipArchive = true

  /**
   * If {@code true} JetBrains RE jre will be added to a zip archive
   */
  boolean zipArchiveWithBundledJre = true

  /**
   * If {@code true} Windows Installer will associate *.ipr files with the IDE in Registry
   */
  boolean associateIpr = true

  /**
   * Path to a directory containing images for installer: logo.bmp, headerlogo.bmp, install.ico, uninstall.ico
   */
  String installerImagesPath

  /**
   * List of file extensions (without leading dot) which installer will suggest to associate with the product
   */
  List<String> fileAssociations = []

  /**
   * Paths to files which will be used to overwrite the standard *.nsi files
   */
  List<String> customNsiConfigurationFiles = []

  /**
   * Path to a file which contains set of properties to manage UI options when installing the product in silent mode. If {@code null}
   * the default platform/build-scripts/resources/win/nsis/silent.config will be used.
   */
  String silentInstallationConfig = null

  /**
   * Name of the root directory in Windows .zip archive
   */
  @SuppressWarnings('GrMethodMayBeStatic')
  // method is used by AndroidStudioProperties.groovy (https://bit.ly/3heXKlQ)
  String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    return ""
  }

  /**
   * Name of the root product windows installation directory and Desktop ShortCut
   */
  String getNameForInstallDirAndDesktopShortcut(ApplicationInfoProperties applicationInfo, String buildNumber) {
    "${getFullNameIncludingEdition(applicationInfo)} ${applicationInfo.isEAP ? buildNumber : applicationInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to Windows distribution of the product.
   * @param targetDirectory contents of this directory will be packed into zip archive and exe installer, so when the product is installed
   * it'll be placed under its root directory.
   */
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    RepairUtilityBuilder.bundle(context, OsFamily.WINDOWS, JvmArchitecture.x64, Paths.get(targetDirectory))
  }

  /**
   * The returned name will be shown in Windows Installer and used in Registry keys
   */
  String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { applicationInfo.productName }
  /**
   * The returned name will be used to create links on Desktop
   */
  String getFullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { applicationInfo.shortCompanyName + " " + getFullNameIncludingEdition(applicationInfo) }

  String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
    return null
  }

  /**
   * Relative paths to files not in `bin` directory of Windows distribution which should be signed
   */
  List<String> getBinariesToSign(BuildContext context) {
    return []
  }
}
