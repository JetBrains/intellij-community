// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Path

/**
 * This extension point makes it possible to select which JDK should be configured for the project,
 * based on the configuration file of an external SDK manager.
 *
 * @param T Release data representation for the configuration provider.
 */
public interface ExternalJavaConfigurationProvider<T : JdkReleaseData> {

  public companion object {
    public val EP_NAME: ExtensionPointName<ExternalJavaConfigurationProvider<*>> = ExtensionPointName.create("com.intellij.openapi.projectRoots.externalJavaConfigurationProvider")
  }

  /**
   * @return true if the file with given [fileName] is supported by the configuration provider.
   */
  public fun isConfigurationFile(fileName: String): Boolean

  /**
   * @return the path to the configuration file supported by this provider.
   */
  public fun getConfigurationFilePath(project: Project): Path

  /**
   * @return range of the release data in the configuration file.
   */
  public fun getReleaseDataOffset(text: String): TextRange?

  /**
   * @return the release data [T] corresponding to the [text] content of the configuration file.
   */
  public fun getReleaseData(text: String): T?

  /**
   * @return the command to be executed in the terminal to download the release data JDK,
   * null if downloads are not supported.
   */
  public fun getDownloadCommandFor(releaseData: T): String?
}

/**
 * Represents the release data parsed from an external SDK manager configuration file.
 */
public interface JdkReleaseData {
  public fun getVariant(): JdkVersionDetector.Variant

  public fun matchVersionString(versionString: @NlsSafe String): Boolean

  public fun matchAgainstSdk(sdk: Sdk): Boolean {
    val versionString = sdk.versionString ?: return false
    return matchVersionString(versionString)
  }

  public fun matchAgainstPath(path: String): Boolean {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return false
    return matchVersionString(info.displayVersionString())
  }
}