// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
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
inline fun windowsCustomizer(projectHome: Path, configure: WindowsCustomizerBuilder.() -> Unit): WindowsDistributionCustomizer {
  return WindowsCustomizerBuilder(projectHome).apply(configure).build()
}

/**
 * Builder class for creating [WindowsDistributionCustomizer] instances using a DSL.
 */
@WindowsCustomizerDsl
class WindowsCustomizerBuilder @PublishedApi internal constructor(private val projectHome: Path) {
  /**
   * Path to 256x256 *.ico file, relative to projectHome.
   * Will be automatically prefixed with projectHome during build.
   */
  var icoPath: String? = null
  
  /**
   * Path to *.ico file for EAP builds, relative to projectHome.
   * If null, [icoPath] will be used. Will be automatically prefixed with projectHome during build.
   */
  var icoPathForEAP: String? = null
  
  /**
   * Path to installer images directory, relative to projectHome.
   * Should contain: logo.bmp, headerlogo.bmp, install.ico, uninstall.ico.
   * Will be automatically prefixed with projectHome during build.
   */
  var installerImagesPath: String? = null
  
  /**
   * List of file extensions (without leading dots) which the installer will suggest to associate with the product.
   * Example: listOf("java", "kt", "gradle")
   */
  var fileAssociations: List<String> = emptyList()
  
  /**
   * If true, Windows Installer will associate *.ipr files with the IDE in Registry.
   */
  var associateIpr: Boolean = true
  
  /**
   * If true, *.bat files (productName.bat and inspect.bat) will be included in the distribution.
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
   * Suffix for ZIP archive with bundled JRE.
   */
  var zipArchiveWithBundledJreSuffix: String = ".win"
  
  /**
   * Suffix for ZIP archive without bundled JRE.
   */
  var zipArchiveWithoutBundledJreSuffix: String = "-no-jbr.win"
  
  /**
   * Paths to files which will be used to overwrite the standard *.nsi files.
   * Paths will be automatically prefixed with projectHome during build.
   */
  var customNsiConfigurationFiles: List<String> = emptyList()
  
  // Method override handlers (stored as lambdas)
  private var copyAdditionalFilesHandler: (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = null
  private var fullNameHandler: ((ApplicationInfoProperties) -> String)? = null
  private var alternativeFullNameHandler: ((ApplicationInfoProperties) -> String?)? = null
  private var fullNameAndVendorHandler: ((ApplicationInfoProperties) -> String)? = null
  private var uninstallFeedbackUrlHandler: ((ApplicationInfoProperties) -> String?)? = null
  private var binariesToSignHandler: ((BuildContext) -> List<String>)? = null
  
  /**
   * Gets the current copyAdditionalFiles handler for wrapping purposes.
   * @return the current handler, or null if none is set
   */
  fun getCopyAdditionalFilesHandler(): (suspend (Path, JvmArchitecture, BuildContext) -> Unit)? = copyAdditionalFilesHandler
  
  /**
   * Adds custom logic for copying additional files to the Windows distribution.
   * This handler is called after the base copyAdditionalFiles logic.
   *
   * @param handler Lambda receiving targetDir, arch, and context
   */
  fun copyAdditionalFiles(handler: suspend (targetDir: Path, arch: JvmArchitecture, context: BuildContext) -> Unit) {
    this.copyAdditionalFilesHandler = handler
  }
  
  /**
   * Sets a custom full name including edition for the Windows Installer and Registry keys.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and returning the full name
   */
  fun fullName(handler: (ApplicationInfoProperties) -> String) {
    this.fullNameHandler = handler
  }
  
  /**
   * Sets an alternative full name used by Windows Installer to look for previous versions.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and returning the alternative name
   */
  fun alternativeFullName(handler: (ApplicationInfoProperties) -> String?) {
    this.alternativeFullNameHandler = handler
  }
  
  /**
   * Sets the full name including edition and vendor used to create Desktop links.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and returning the full name with vendor
   */
  fun fullNameAndVendor(handler: (ApplicationInfoProperties) -> String) {
    this.fullNameAndVendorHandler = handler
  }
  
  /**
   * Sets the uninstall feedback page URL shown after uninstallation.
   *
   * @param handler Lambda receiving ApplicationInfoProperties and returning the URL
   */
  fun uninstallFeedbackUrl(handler: (ApplicationInfoProperties) -> String?) {
    this.uninstallFeedbackUrlHandler = handler
  }
  
  /**
   * Sets which binaries (not in bin directory) should be signed.
   *
   * @param handler Lambda receiving BuildContext and returning a list of relative paths
   */
  fun binariesToSign(handler: (BuildContext) -> List<String>) {
    this.binariesToSignHandler = handler
  }
  
  /**
   * Builds the [WindowsDistributionCustomizer] with the configured settings.
   * Automatically prefixes relative paths with projectHome.
   */
  fun build(): WindowsDistributionCustomizer {
    return WindowsDistributionCustomizerImpl(builder = this, projectHome = projectHome)
  }

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
      builder.copyAdditionalFilesHandler?.invoke(targetDir, arch, context)
    }

    override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String {
      return builder.fullNameHandler?.invoke(appInfo) ?: super.getFullNameIncludingEdition(appInfo)
    }

    override fun getAlternativeFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String? {
      return builder.alternativeFullNameHandler?.invoke(appInfo) ?: super.getAlternativeFullNameIncludingEdition(appInfo)
    }

    override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String {
      return builder.fullNameAndVendorHandler?.invoke(appInfo) ?: super.getFullNameIncludingEditionAndVendor(appInfo)
    }

    override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String? {
      return builder.uninstallFeedbackUrlHandler?.invoke(appInfo) ?: super.getUninstallFeedbackPageUrl(appInfo)
    }

    override fun getBinariesToSign(context: BuildContext): List<String> {
      return builder.binariesToSignHandler?.invoke(context) ?: super.getBinariesToSign(context)
    }
  }
}

/**
 * Creates a [WindowsDistributionCustomizer] with Community edition defaults using a builder DSL.
 *
 * Example usage:
 * ```kotlin
 * communityWindowsCustomizer(projectHome) {
 *   // Override or extend Community defaults
 *   fileAssociations += "xml"
 * }
 * ```
 */
inline fun communityWindowsCustomizer(projectHome: Path, configure: WindowsCustomizerBuilder.() -> Unit = {}): WindowsDistributionCustomizer {
  return windowsCustomizer(projectHome) {
    // Set Community defaults
    icoPath = "build/conf/ideaCE/win/images/idea_CE.ico"
    icoPathForEAP = "build/conf/ideaCE/win/images/idea_CE_EAP.ico"
    installerImagesPath = "build/conf/ideaCE/win/images"
    fileAssociations = listOf("java", "gradle", "groovy", "kt", "kts", "pom")
    
    fullName { "IntelliJ IDEA Community Edition" }
    
    fullNameAndVendor { "IntelliJ IDEA Community Edition" }
    
    uninstallFeedbackUrl { appInfo ->
      "https://www.jetbrains.com/idea/uninstall/?edition=IC-${appInfo.majorVersion}.${appInfo.minorVersion}"
    }
    
    // Apply user configuration
    configure()
  }
}

abstract class WindowsDistributionCustomizer {
  /**
   * Path to 256x256 *.ico file for Windows distribution.
   */
  var icoPath: Path? = null

  /**
   * Path to an ico file for EAP builds (if `null` [icoPath] will be used).
   */
  var icoPathForEAP: Path? = null

  /**
   * If `true`, *.bat files (productName.bat and inspect.bat) will be included in the distribution.
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
   * If `true`, Windows Installer will associate *.ipr files with the IDE in Registry.
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
   * Paths to files which will be used to overwrite the standard *.nsi files.
   */
  open val customNsiConfigurationFiles: List<Path>
    get() = emptyList()

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
  open suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
    RepairUtilityBuilder.bundle(OsFamily.WINDOWS, arch, targetDir, context)
  }

  /**
   * The returned name will be shown in the Windows Installer and used in Registry keys.
   */
  open fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = appInfo.fullProductName

  /**
   * The returned name will be used in the Windows Installer to look for previous versions.
   */
  open fun getAlternativeFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String? = null

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
