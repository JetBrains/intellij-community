// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import java.nio.file.Path

open class WindowsDistributionCustomizer {
  /**
   * Path to 256x256 *.ico file for Windows distribution.
   */
  var icoPath: String? = null

  /**
   * Path to an ico file for EAP builds (if `null` [icoPath] will be used).
   */
  var icoPathForEAP: String? = null

  /**
   * If `true`, *.bat files (productName.bat and inspect.bat) will be included in the distribution.
   */
  var includeBatchLaunchers = true

  /**
   * If `true`, build a ZIP archive with JetBrains Runtime.
   */
  var buildZipArchiveWithBundledJre = true

  /**
   * If `true`, build a ZIP archive without JetBrains Runtime.
   */
  var buildZipArchiveWithoutBundledJre = false

  var zipArchiveWithBundledJreSuffix = ".win"
  var zipArchiveWithoutBundledJreSuffix = "-no-jbr.win"

  /**
   * If `true`, Windows Installer will associate *.ipr files with the IDE in Registry.
   */
  var associateIpr = true

  /**
   * Path to a directory containing images for installer: `logo.bmp`, `headerlogo.bmp`, `install.ico`, `uninstall.ico`.
   */
  @Suppress("SpellCheckingInspection")
  var installerImagesPath: String? = null

  /**
   * List of file extensions (without a leading dot) which the installer will suggest to associate with the product.
   */
  var fileAssociations: List<String> = emptyList()

  /**
   * Paths to files which will be used to overwrite the standard *.nsi files.
   */
  var customNsiConfigurationFiles: PersistentList<String> = persistentListOf()

  /**
   * Name of the root directory in Windows .zip archive
   * (the method is overridden in [AndroidStudioProperties.groovy](https://bit.ly/3heXKlQ)).
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = ""

  /**
   * Name of the Windows installation directory and Desktop shortcut.
   */
  open fun getNameForInstallDirAndDesktopShortcut(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "${getFullNameIncludingEdition(appInfo)} ${if (appInfo.isEAP) buildNumber else appInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to the Windows distribution of the product.
   */
  open suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
    RepairUtilityBuilder.bundle(context, OsFamily.WINDOWS, arch, targetDir)
  }

  /**
   * The returned name will be shown in Windows Installer and used in Registry keys.
   */
  open fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = appInfo.fullProductName

  /**
   * The returned name will be used to create links on Desktop.
   */
  open fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String {
    return appInfo.shortCompanyName + ' ' + getFullNameIncludingEdition(appInfo)
  }

  open fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? = null

  /**
   * Relative paths to files not in `bin` directory of Windows distribution which should be signed.
   */
  open fun getBinariesToSign(context: BuildContext): List<String> = listOf()
}
