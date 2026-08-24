// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.project.stateStore
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Path
import java.util.Properties

public data class SdkmanReleaseData(val target: String,
                                    val version: String,
                                    val flavour: String? = null,
                                    val vendor: String? = null) : JdkReleaseData {
  public companion object {
    private val versionRegex: Regex = Regex("\\d+(?:\\.\\d+)*")
    private val vendorRegex: Regex = Regex("[a-z]+")

    /**
     * Parses a SDKMAN! java candidate identifier, as listed by `sdk list java`.
     *
     * An identifier consists of a dot-separated [version], an optional [flavour] holding the remaining
     * qualifiers and build metadata (`fx`, `ea.11`, `hs`, `+1.1`, `r25`, ...),
     * and an optional [vendor] suffix separated by the last `-`.
     *
     * For example: `8.0.504-amzn`, `28.0.0+ea.11-open`, `25.0.4-fx+1.1-librca`, `16.0.1.hs-adpt`.
     */
    public fun parse(text: String): SdkmanReleaseData? {
      if (text.isEmpty() || !text[0].isDigit()) return null

      var rest = text
      var vendor: String? = null
      val separatorIndex = text.lastIndexOf('-')
      if (separatorIndex > 0) {
        val suffix = text.substring(separatorIndex + 1)
        if (vendorRegex.matches(suffix)) {
          vendor = suffix
          rest = text.substring(0, separatorIndex)
        }
      }

      val version = versionRegex.matchAt(rest, 0)?.value ?: return null
      val flavour = rest.substring(version.length).trimStart('.', '-', '+').takeIf { it.isNotEmpty() }
      return SdkmanReleaseData(text, version, flavour, vendor)
    }
  }

  override val javaVersion: JavaVersion? = JavaVersion.tryParse(version)

  override val variant: JdkVersionDetector.Variant = when (vendor) {
      "adpt" if flavour == "hs" -> JdkVersionDetector.Variant.AdoptOpenJdk_HS
      "adpt" if flavour == "j9" -> JdkVersionDetector.Variant.AdoptOpenJdk_J9
      "albba" -> JdkVersionDetector.Variant.Dragonwell
      "amzn" -> JdkVersionDetector.Variant.Corretto
      "bsg" -> JdkVersionDetector.Variant.BiSheng
      "graal" -> JdkVersionDetector.Variant.GraalVM
      "graalce" -> JdkVersionDetector.Variant.GraalVMCE
      "jbr" -> JdkVersionDetector.Variant.JBR
      "kona" -> JdkVersionDetector.Variant.Kona
      "librca", "librcafx", "nik" -> JdkVersionDetector.Variant.Liberica
      "ms" -> JdkVersionDetector.Variant.Microsoft
      "oracle" -> JdkVersionDetector.Variant.Oracle
      "open" -> JdkVersionDetector.Variant.Oracle
      "sapmchn" -> JdkVersionDetector.Variant.SapMachine
      "sem" -> JdkVersionDetector.Variant.Semeru
      "tem" -> JdkVersionDetector.Variant.Temurin
      "zulu", "zulufx" -> JdkVersionDetector.Variant.Zulu
      else -> JdkVersionDetector.Variant.Unknown
  }
}

private const val SDKMANRC = ".sdkmanrc"
private val JAVA_PATTERN: Regex = Regex("^java=(.*)$", RegexOption.MULTILINE)

public class SdkmanrcConfigurationProvider: ExternalJavaConfigurationProvider<SdkmanReleaseData> {
  override fun isConfigurationFile(fileName: String): Boolean = fileName == SDKMANRC

  override fun getConfigurationFilePath(project: Project): Path {
    return project.stateStore.projectBasePath.resolve(SDKMANRC)
  }

  override fun getReleaseData(text: String): SdkmanReleaseData? {
    val properties = Properties().apply {
      load(text.byteInputStream())
    }
    val java = properties.getProperty("java") ?: return null
    return SdkmanReleaseData.parse(java.trim())
  }

  override fun getReleaseDataOffset(text: String): TextRange? {
    val releaseData = getReleaseData(text) ?: return null
    val range = JAVA_PATTERN
      .findAll(text)
      .firstOrNull { it.groupValues.getOrNull(1)?.contains(releaseData.target) == true }
      ?.range ?: return null
    return TextRange(range.first, range.last)
  }

  override fun getDownloadCommandFor(releaseData: SdkmanReleaseData): String {
    return "sdk install java ${releaseData.target}"
  }
}