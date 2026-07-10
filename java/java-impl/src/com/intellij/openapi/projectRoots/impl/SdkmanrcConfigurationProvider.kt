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
    private val regex: Regex = Regex("(\\d+(?:\\.\\d+)*)(?:\\.([^-]+))?-?(\\S*)?")

    public fun parse(text: String): SdkmanReleaseData? {
      val matchResult = regex.matchEntire(text) ?: return null
      return SdkmanReleaseData(
        text,
        matchResult.groups[1]?.value ?: return null,
        matchResult.groups[2]?.value,
        matchResult.groups[3]?.value
      )
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
      "librca" -> JdkVersionDetector.Variant.Liberica
      "ms" -> JdkVersionDetector.Variant.Microsoft
      "oracle" -> JdkVersionDetector.Variant.Oracle
      "open" -> JdkVersionDetector.Variant.Oracle
      "sapmchn" -> JdkVersionDetector.Variant.SapMachine
      "sem" -> JdkVersionDetector.Variant.Semeru
      "tem" -> JdkVersionDetector.Variant.Temurin
      "zulu" -> JdkVersionDetector.Variant.Zulu
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