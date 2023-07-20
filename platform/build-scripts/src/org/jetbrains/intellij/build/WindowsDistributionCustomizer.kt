// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path

abstract class WindowsDistributionCustomizer {
  /**
   * Path to 256x256 *.ico file for Windows distribution
   */
  var icoPath: String? = null

  /**
   * Path to ico file for EAP builds (if `null` [icoPath] will be used)
   */
  var icoPathForEAP: String? = null

  /**
   * If `true` *.bat files (productName.bat and inspect.bat) will be included into the distribution
   */
  var includeBatchLaunchers = true

  /**
   * If `true` build a zip archive with JetBrains Runtime
   */
  var buildZipArchiveWithBundledJre = true

  /**
   * If `true` build a zip archive without JetBrains Runtime
   */
  var buildZipArchiveWithoutBundledJre = false

  var zipArchiveWithBundledJreSuffix = ".win"
  var zipArchiveWithoutBundledJreSuffix = "-no-jbr.win"

  /**
   * If `true` Windows Installer will associate *.ipr files with the IDE in Registry
   */
  var associateIpr = true

  /**
   * Path to a directory containing images for installer: logo.bmp, headerlogo.bmp, install.ico, uninstall.ico
   */
  var installerImagesPath: String? = null

  /**
   * List of file extensions (without leading dot) which installer will suggest to associate with the product
   */
  var fileAssociations: List<String> = emptyList()

  /**
   * Paths to files which will be used to overwrite the standard *.nsi files
   */
  var customNsiConfigurationFiles: PersistentList<String> = persistentListOf()

  /**
   * Name of the root directory in Windows .zip archive
   */
  // method is used by AndroidStudioProperties.groovy (https://bit.ly/3heXKlQ)
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = ""

  /**
   * Name of the root product windows installation directory and Desktop ShortCut
   */
  open fun getNameForInstallDirAndDesktopShortcut(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "${getFullNameIncludingEdition(appInfo)} ${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"

  /**
   * Override this method to copy additional files to Windows distribution of the product.
   * @param targetDirectory contents of this directory will be packed into zip archive and exe installer, so when the product is installed
   * it'll be placed under its root directory.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: Path, arch: JvmArchitecture) {
    RepairUtilityBuilder.bundle(context, OsFamily.WINDOWS, arch, targetDirectory)

    copyAdditionalFilesBlocking(context, targetDirectory)
  }

  open fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path) {
  }

  /**
   * The returned name will be shown in Windows Installer and used in Registry keys
   */
  open fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = appInfo.productName

  /**
   * The returned name will be used to create links on Desktop
   */
  open fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String =
    appInfo.shortCompanyName + " " + getFullNameIncludingEdition(appInfo)

  open fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? {
    return null
  }

  /**
   * Relative paths to files not in `bin` directory of Windows distribution which should be signed
   */
  open fun getBinariesToSign(context: BuildContext): List<String>  {
    return listOf()
  }
}
