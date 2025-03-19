// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.File

private val LOG = logger<ToolVersionsConfigurationProvider>()

data class AsdfReleaseData(val name: String, val vendor: String, val version: String) {
  companion object {
    private val regex: Regex = Regex("([\\w\\-]+)-([0-9][^-_+]*)")

    fun parse(text: String): AsdfReleaseData? {
      val matchResult = regex.find(text) ?: return null
      return AsdfReleaseData(
        text,
        matchResult.groups[1]?.value ?: return null,
        matchResult.groups[2]?.value ?: return null,
      )
    }
  }

  fun matchVersionString(versionString: @NlsSafe String): Boolean {
    LOG.info("Matching '$versionString'")
    if (version !in versionString) return false

    val variant = when (vendor) {
      "adoptopenjdk", "adoptopenjdk-jre" -> JdkVersionDetector.Variant.AdoptOpenJdk_HS
      "adoptopenjdk-jre-openj9", "adoptopenjdk-jre-openj9-large_heap",
      "adoptopenjdk-openj9", "adoptopenjdk-openj9-large_heap" -> JdkVersionDetector.Variant.AdoptOpenJdk_J9
      "corretto" -> JdkVersionDetector.Variant.Corretto
      "dragonwell" -> JdkVersionDetector.Variant.Dragonwell
      "graalvm-community" -> JdkVersionDetector.Variant.GraalVMCE
      "kona" -> JdkVersionDetector.Variant.Kona
      "liberica", "liberica-javafx",
      "liberica-jre", "liberica-jre-javafx", "liberica-lite" -> JdkVersionDetector.Variant.Liberica
      "microsoft" -> JdkVersionDetector.Variant.Microsoft
      "openjdk", "oracle" -> JdkVersionDetector.Variant.Oracle
      "oracle-graalvm" -> JdkVersionDetector.Variant.GraalVM
      "sapmachine", "sapmachine-jre" -> JdkVersionDetector.Variant.SapMachine
      "semeru-jre-openj9", "semeru-openj9" -> JdkVersionDetector.Variant.Semeru
      "temurin", "temurin-jre" -> JdkVersionDetector.Variant.Temurin
      "zulu", "zulu-javafx", "zulu-jre", "zulu-jre-javafx" -> JdkVersionDetector.Variant.Zulu
      else -> JdkVersionDetector.Variant.Unknown
    }

    // Check vendor
    val variantName = variant.displayName
    return variantName != null && versionString.contains(variantName)
  }
}

class ToolVersionsConfigurationProvider : ExternalJavaConfigurationProvider<AsdfReleaseData> {
  override fun getConfigurationFile(project: Project): File = File(project.basePath, ".tool-versions")

  override fun getReleaseData(text: String): AsdfReleaseData? {
    val releaseDataText = text.lines()
      .find { it.split(" ").firstOrNull() == "java" }
      ?.substringAfter(" ") ?: return null
    return AsdfReleaseData.parse(releaseDataText)
  }

  override fun matchAgainstSdk(releaseData: AsdfReleaseData, sdk: Sdk): Boolean {
    val sdkVersion = sdk.versionString ?: return false
    return releaseData.matchVersionString(sdkVersion)
  }

  override fun matchAgainstPath(releaseData: AsdfReleaseData, path: String): Boolean {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return false
    return releaseData.matchVersionString(info.displayVersionString())
  }
}