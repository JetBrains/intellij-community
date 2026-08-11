// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.lang.JavaVersion
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

public enum class ReleaseDataMatching {
  EXACT_MATCH, FEATURE_MATCH, NO_MATCH
}

/**
 * Represents the release data parsed from an external SDK manager configuration file.
 */
public interface JdkReleaseData {
  public val variant: JdkVersionDetector.Variant
  public val javaVersion: JavaVersion?

  public fun matchVariant(variant: JdkVersionDetector.Variant): Boolean {
    if (variant == JdkVersionDetector.Variant.Homebrew) {
      return this.variant in listOf(JdkVersionDetector.Variant.Homebrew, JdkVersionDetector.Variant.Oracle)
    }
    return this.variant == variant
  }

  public fun matchVersion(version: JavaVersion): ReleaseDataMatching {
    if (javaVersion == null) return ReleaseDataMatching.NO_MATCH

    return when {
      javaVersion == version -> ReleaseDataMatching.EXACT_MATCH
      javaVersion!!.feature == version.feature -> ReleaseDataMatching.FEATURE_MATCH
      else -> ReleaseDataMatching.NO_MATCH
    }
  }

  public fun matchVersionString(versionString: @NlsSafe String): ReleaseDataMatching {
    val variant = JdkVersionDetector.Variant.entries
                    .filter { it != JdkVersionDetector.Variant.Unknown && versionString.contains(it.displayName) }
                    .maxByOrNull { it.displayName.length }
                  ?: JdkVersionDetector.Variant.Unknown
    if (!matchVariant(variant)) return ReleaseDataMatching.NO_MATCH
    // Strip variants with digits (e.g. "AdoptOpenJDK (OpenJ9)") so JavaVersion.parse doesn't confuse them with the version number.
    // Keep other variants for JavaVersion.parse to apply keyword handling (e.g. trimming from "Java " for GraalVM).
    val stringForVersionParse = when {
        variant != JdkVersionDetector.Variant.Unknown && variant.displayName.any { it.isDigit() } ->
          versionString.replaceFirst(variant.displayName, "")
        else -> versionString
    }
    val version = JavaVersion.tryParse(stringForVersionParse) ?: return ReleaseDataMatching.NO_MATCH
    return matchVersion(version)
  }

  public fun matchAgainstSdk(sdk: Sdk): ReleaseDataMatching {
    val versionString = sdk.versionString ?: return ReleaseDataMatching.NO_MATCH
    return matchVersionString(versionString)
  }

  public fun matchAgainstPath(path: String): ReleaseDataMatching {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return ReleaseDataMatching.NO_MATCH
    if (!matchVariant(info.variant)) return ReleaseDataMatching.NO_MATCH
    return matchVersion(info.version)
  }

  public fun matchAgainstItem(item: JdkItem): ReleaseDataMatching {
    val variant = item.detectVariant()
    if (!matchVariant(variant)) return ReleaseDataMatching.NO_MATCH
    return matchVersion(JavaVersion.parse(item.versionString))
  }
}