// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.TextRange
import java.nio.file.Path

/**
 * This extension point makes it possible to select which JDK should be configured for the project,
 * based on the configuration file of an external SDK manager.
 *
 * @param T Release data representation for the configuration provider.
 */
public interface ExternalJavaConfigurationProvider<T> {

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
   * @return true if the release data matches the given SDK.
   */
  public fun matchAgainstSdk(releaseData: T, sdk: Sdk): Boolean

  /**
   * @return true if the release data matches the given path.
   */
  public fun matchAgainstPath(releaseData: T, path: String): Boolean

  /**
   * @return the command to be executed in the terminal to download the release data JDK,
   * null if downloads are not supported.
   */
  public fun getDownloadCommandFor(releaseData: T): String?
}