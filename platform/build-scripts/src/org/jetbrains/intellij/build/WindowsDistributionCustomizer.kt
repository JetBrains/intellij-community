// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.support.bundleRepairUtility
import java.nio.file.Path

/**
 * DSL marker annotation for Windows customizer builder DSL.
 * Prevents implicit receiver leakage in nested scopes.
 */
@DslMarker
annotation class WindowsCustomizerDsl

/**
 * Creates a [WindowsDistributionCustomizer] using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * windowsCustomizer(projectHome) {
 *   icoPath = "build/images/win/idea.ico"
 *   icoPathForEAP = "build/images/win/idea_EAP.ico"
 *   installerImagesPath = "build/images/win"
 *   fileAssociations = listOf("java", "kt", "gradle")
 *   associateIpr = false
 *
 *   copyAdditionalFiles { targetDir, arch, context ->
 *     // Custom file copying logic
 *   }
 *
 *   uninstallFeedbackUrl { appInfo ->
 *     "https://example.com/uninstall?v=${appInfo.majorVersion}"
 *   }
 * }
 * ```
 */
inline fun windowsCustomizer(projectHome: Path, configure: WindowsCustomizerBuilder.() -> Unit = {}): WindowsDistributionCustomizer {
  return WindowsCustomizerBuilder(projectHome).apply(configure).build()
}

/**
 * Builder class for creating [WindowsDistributionCustomizer] instances using a DSL.
 */
@WindowsCustomizerDsl
class WindowsCustomizerBuilder @PublishedApi internal constructor(private val projectHome: Path) {
  /**
   * Path to a 256x256 .ico file, relative to [projectHome].
   */
  var icoPath: String? = null

  /**
   * Path to a .ico file for EAP builds, relative to [projectHome]. If `null`, [icoPath] will be used.
   */
  var icoPathForEAP: String? = null

  /**
   * Path to the installer images directory, relative to [projectHome].
   * Should contain: logo.bmp, headerlogo.bmp, install.ico, uninstall.ico.
   */
  var installerImagesPath: String? = null

  /**
   * List of file extensions (without leading dots) which the installer will suggest to associate with the product.
   * Example: `listOf("java", "kt", "gradle")`
   */
  var fileAssociations: List<String> = emptyList()

  /**
   * If `true`, Windows Installer will associate .ipr files with the IDE in Registry.
   */
  var associateIpr: Boolean = true

  /**
   * If `true`, .bat files ("<productName>.bat" and "inspect.bat") will be included in the distribution.
   */
  var includeBatchLaunchers: Boolean = true

  /**
   * If true, build a ZIP archive with JetBrains Runtime.
   */
  var buildZipArchiveWithBundledJre: Boolean = true

  /**
   * If true, build a ZIP archive without JetBrains Runtime.
   */
  var buildZipArchiveWithoutBundledJre: Boolean = false

  /**
   * Suffix for the ZIP archive with bundled JRE.
   */
  var zipArchiveWithBundledJreSuffix: String = ".win"

  /**
   * Suffix for the ZIP archive without bundled JRE.
   */
  var zipArchiveWithoutBundledJreSuffix: String = "-no-jbr.win"

  /**
   * Paths to files which will be used to overwrite the standard .nsi files, relative to [projectHome].
   */
  var customNsiConfigurationFiles: List<String> = emptyList()

  // Method override handlers (stored as lambdas)
  private var copyAdditionalFilesHandler: (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = null
  private var fullNameHandler: ((ApplicationInfoProperties) -> String)? = null
  private var alternativeFullNameHandler: ((ApplicationInfoProperties) -> String?)? = null
  private var uninstallFeedbackUrlHandler: ((ApplicationInfoProperties) -> String?)? = null
  private var binariesToSignHandler: ((BuildContext) -> List<String>)? = null
  private var installDirNameHandler: ((BuildContext) -> String)? = null

  /**
   * Gets the current copyAdditionalFiles handler for wrapping purposes.
   * @return the current handler, or null if none is set
   */
  fun getCopyAdditionalFilesHandler(): (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = copyAdditionalFilesHandler

  /**
   * Adds custom logic for copying additional files to the Windows distribution.
   * This handler is called after the base copyAdditionalFiles logic.
   *
   * @see [ProductProperties.copyAdditionalOsSpecificFiles]
   */
  fun copyAdditionalFiles(handler: suspend (targetDir: Path, arch: JvmArchitecture, context: BuildContext) -> Unit) {
    copyAdditionalFilesHandler = handler
  }

  /**
   * Sets a custom full name including edition for the Windows Installer and Registry keys.
   */
  fun fullName(handler: (ApplicationInfoProperties) -> String) {
    fullNameHandler = handler
  }

  /**
   * Sets an alternative full name used by Windows Installer to look for previous versions.
   */
  fun alternativeFullName(handler: (ApplicationInfoProperties) -> String?) {
    alternativeFullNameHandler = handler
  }

  /**
   * Sets the uninstallation feedback page URL shown after uninstallation.
   */
  fun uninstallFeedbackUrl(handler: (ApplicationInfoProperties) -> String?) {
    uninstallFeedbackUrlHandler = handler
  }

  /**
   * Sets additional binaries to sign, relative to [projectHome].
   * Files in the "bin/" directory are always signed.
   */
  @Suppress("unused")
  fun binariesToSign(handler: (BuildContext) -> List<String>) {
    binariesToSignHandler = handler
  }

  /**
   * Sets the default name for the installation directory.
   */
  fun installDirNameHandler(handler: (BuildContext) -> String) {
    installDirNameHandler = handler
  }

  /**
   * Builds the [WindowsDistributionCustomizer] with the configured settings.
   * Automatically prefixes relative paths with [projectHome].
   */
  fun build(): WindowsDistributionCustomizer = WindowsDistributionCustomizerImpl(builder = this, projectHome)

  private class WindowsDistributionCustomizerImpl(
    private val builder: WindowsCustomizerBuilder,
    private val projectHome: Path,
  ) : WindowsDistributionCustomizer() {
    init {
      icoPath = builder.icoPath?.let { projectHome.resolve(it) }
      icoPathForEAP = builder.icoPathForEAP?.let { projectHome.resolve(it) }
      installerImagesPath = builder.installerImagesPath?.let { projectHome.resolve(it) }
      includeBatchLaunchers = builder.includeBatchLaunchers
      buildZipArchiveWithBundledJre = builder.buildZipArchiveWithBundledJre
      buildZipArchiveWithoutBundledJre = builder.buildZipArchiveWithoutBundledJre
      zipArchiveWithBundledJreSuffix = builder.zipArchiveWithBundledJreSuffix
      zipArchiveWithoutBundledJreSuffix = builder.zipArchiveWithoutBundledJreSuffix
    }

    override val associateIpr: Boolean
      get() = builder.associateIpr

    override val fileAssociations: List<String>
      get() = builder.fileAssociations

    override val customNsiConfigurationFiles: List<Path>
      get() = builder.customNsiConfigurationFiles.map { projectHome.resolve(it) }

    override suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
      super.copyAdditionalFiles(targetDir, arch, context)
      context.productProperties.copyAdditionalOsSpecificFiles(targetDir, OsFamily.WINDOWS, arch, context)
      builder.copyAdditionalFilesHandler?.invoke(targetDir, arch, context)
    }

    override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String {
      return builder.fullNameHandler?.invoke(appInfo) ?: super.getFullNameIncludingEdition(appInfo)
    }

    override fun getAlternativeFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String? {
      return builder.alternativeFullNameHandler?.invoke(appInfo) ?: super.getAlternativeFullNameIncludingEdition(appInfo)
    }

    override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? {
      return builder.uninstallFeedbackUrlHandler?.invoke(appInfo) ?: super.getUninstallFeedbackPageUrl(appInfo)
    }

    override fun getBinariesToSign(context: BuildContext): List<String> {
      return builder.binariesToSignHandler?.invoke(context) ?: super.getBinariesToSign(context)
    }

    override fun getNameForInstallDirAndDesktopShortcut(context: BuildContext): String {
      return builder.installDirNameHandler?.invoke(context) ?: super.getNameForInstallDirAndDesktopShortcut(context)
    }
  }
}

abstract class WindowsDistributionCustomizer {
  /**
   * Path to a 256x256 .ico file for Windows distribution.
   */
  var icoPath: Path? = null

  /**
   * Path to a .ico file for EAP builds (if `null` [icoPath] will be used).
   */
  var icoPathForEAP: Path? = null

  /**
   * If `true`, a GUI .exe launcher will be included in the distribution.
   */
  var includeGuiLauncher: Boolean = true

  /**
   * If `true`, a console .exe launcher will be included in the distribution.
   */
  var includeConsoleLauncher: Boolean = true

  /**
   * If `true`, .bat files ("<productName>.bat" and "inspect.bat") will be included in the distribution.
   */
  var includeBatchLaunchers: Boolean = true

  /**
   * If `true`, build a ZIP archive with JetBrains Runtime.
   */
  var buildZipArchiveWithBundledJre: Boolean = true

  /**
   * If `true`, build a ZIP archive without JetBrains Runtime.
   */
  var buildZipArchiveWithoutBundledJre: Boolean = false

  var zipArchiveWithBundledJreSuffix: String = ".win"
  var zipArchiveWithoutBundledJreSuffix: String = "-no-jbr.win"

  /**
   * If `true`, Windows Installer will associate .ipr files with the IDE in Registry.
   */
  open val associateIpr: Boolean
    get() = true

  /**
   * Path to a directory containing images for installer: `logo.bmp`, `headerlogo.bmp`, `install.ico`, `uninstall.ico`.
   */
  var installerImagesPath: Path? = null

  /**
   * Set to `false` for products that are not updated with patches.
   */
  var publishUninstaller: Boolean = true

  /**
   * List of file extensions (without a leading dot) which the installer will suggest to associate with the product.
   */
  open val fileAssociations: List<String>
    get() = emptyList()

  /**
   * Paths to files that will be used to overwrite the standard .nsi files.
   */
  open val customNsiConfigurationFiles: List<Path>
    get() = emptyList()

  /**
   * Name of the root directory in the Windows ZIP archive
   * (the method is overridden in [AndroidStudioProperties.groovy](https://bit.ly/3heXKlQ)).
   */
  open fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = ""

  /**
   * Name of the Windows installation directory and Desktop shortcut.
   */
  open fun getNameForInstallDirAndDesktopShortcut(context: BuildContext): String {
    val appInfo = context.applicationInfo
    return "${getFullNameIncludingEdition(appInfo)} ${if (appInfo.isEAP) context.buildNumber else appInfo.fullVersion}"
  }

  /**
   * Override this method to copy additional files to the Windows distribution of the product.
   */
  open suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
    bundleRepairUtility(OsFamily.WINDOWS, arch, targetDir, context)
  }

  /**
   * The returned name will be shown in the Windows Installer and used in Registry keys.
   */
  open fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = appInfo.fullProductName

  /**
   * The returned name will be used in the Windows Installer to look for previous versions.
   */
  open fun getAlternativeFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String? = null

  open fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? = null

  /**
   * Relative paths to files not in `bin` directory of Windows distribution which should be signed.
   */
  open fun getBinariesToSign(context: BuildContext): List<String> = listOf()
}
