// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import java.nio.file.Path
import kotlin.io.path.exists

internal fun locateIcoFileForWindowsLauncher(customizer: WindowsDistributionCustomizer, context: BuildContext): Path {
  if (context.applicationInfo.isEAP) {
    val eapIcoPath = customizer.icoPathForEAP ?: context.productProperties.imagesDirectoryPath?.resolve(WINDOWS_ICO_EAP_PATH)?.takeIf { it.exists() }
    if (eapIcoPath != null) {
      return eapIcoPath
    }
  }
  return customizer.icoPath ?: context.productProperties.imagesDirectoryPath?.resolve(WINDOWS_ICO_PATH)
         ?: error("Path to ico file is not specified")
}

internal fun locateIcnsForMacApp(customizer: MacDistributionCustomizer, context: BuildContext): Path {
  if (context.applicationInfo.isEAP) {
    val eapIcnsPath = customizer.icnsPathForEAP ?: context.productProperties.imagesDirectoryPath?.resolve(MAC_ICNS_EAP_PATH)?.takeIf { it.exists() }
    if (eapIcnsPath != null) {
      return eapIcnsPath
    }
  }
  return customizer.icnsPath ?: context.productProperties.imagesDirectoryPath?.resolve(MAC_ICNS_PATH) ?: error("Path to icns file is not specified")
}

internal fun locateDmgImageForMacApp(customizer: MacDistributionCustomizer, context: BuildContext): Path {
  if (context.applicationInfo.isEAP) {
    val eapDmgImagePath = customizer.dmgImagePathForEAP ?: context.productProperties.imagesDirectoryPath?.resolve(MAC_DMG_BACKGROUND_EAP_PATH)?.takeIf { it.exists() }
    if (eapDmgImagePath != null) {
      return eapDmgImagePath
    }
  }
  return customizer.dmgImagePath ?: context.productProperties.imagesDirectoryPath?.resolve(MAC_DMG_BACKGROUND_PATH)
         ?: error("Path to background image for DMG is not specified")
}

internal fun locateIconForLinuxLauncher(customizer: LinuxDistributionCustomizer, context: BuildContext): Path? {
  if (context.applicationInfo.isEAP) {
    val eapIconPath = customizer.iconPngPathForEAP ?: context.productProperties.imagesDirectoryPath?.resolve(LINUX_PRODUCT_EAP_PNG_PATH)?.takeIf { it.exists() }
    if (eapIconPath != null) {
      return eapIconPath
    }
  }
  return customizer.iconPngPath ?: context.productProperties.imagesDirectoryPath?.resolve(LINUX_PRODUCT_PNG_PATH)
}

private const val WINDOWS_ICO_EAP_PATH = "win/product_EAP.ico"
private const val WINDOWS_ICO_PATH = "win/product.ico"
private const val MAC_ICNS_EAP_PATH = "mac/product_EAP.icns"
private const val MAC_ICNS_PATH = "mac/product.icns"
private const val MAC_DMG_BACKGROUND_EAP_PATH = "mac/dmg_background_EAP.tiff"
private const val MAC_DMG_BACKGROUND_PATH = "mac/dmg_background.tiff"
private const val LINUX_PRODUCT_EAP_PNG_PATH = "linux/product_128_EAP.png"
private const val LINUX_PRODUCT_PNG_PATH = "linux/product_128.png"

internal fun verifyThatProductImageFilesExist(imagesDirectoryPath: Path, context: BuildContext) {
  if (context.windowsDistributionCustomizer != null) {
    checkFileExists(imagesDirectoryPath, WINDOWS_ICO_PATH, context)
  }
  if (context.macDistributionCustomizer != null) {
    checkFileExists(imagesDirectoryPath, MAC_ICNS_PATH, context)
    checkFileExists(imagesDirectoryPath, MAC_DMG_BACKGROUND_PATH, context)
  }
  if (context.linuxDistributionCustomizer != null) {
    checkFileExists(imagesDirectoryPath, LINUX_PRODUCT_PNG_PATH, context)
  }
}

private fun checkFileExists(imagesDirectoryPath: Path, relativePath: String, context: BuildContext) {
  if (!imagesDirectoryPath.resolve(relativePath).exists()) {
    context.messages.logErrorAndThrow("Required file '$relativePath' doesn't exist in '$imagesDirectoryPath' specified in ProductProperties.imagesDirectoryPath")
  }
}
