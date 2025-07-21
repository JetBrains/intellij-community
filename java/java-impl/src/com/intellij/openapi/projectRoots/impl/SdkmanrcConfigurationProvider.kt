// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.io.File
import java.util.*

private val LOG = logger<SdkmanrcConfigurationProvider>()

public data class SdkmanReleaseData(val target: String,
                                    val version: String,
                                    val flavour: String? = null,
                                    val vendor: String? = null) {
  public companion object {
    private val regex: Regex = Regex("(\\d+(?:\\.\\d+)*)(?:\\.([^-]+))?-?(.*)?")

    public fun parse(text: String): SdkmanReleaseData? {
      val matchResult = regex.find(text) ?: return null
      return SdkmanReleaseData(
        text,
        matchResult.groups[1]?.value ?: return null,
        matchResult.groups[2]?.value,
        matchResult.groups[3]?.value
      )
    }
  }

  public fun matchVersionString(versionString: @NlsSafe String): Boolean {
    LOG.info("Matching '$versionString'")
    if (version !in versionString) return false

    val variant = when {
      vendor == "adpt" && flavour == "hs" -> JdkVersionDetector.Variant.AdoptOpenJdk_HS
      vendor == "adpt" && flavour == "j9" -> JdkVersionDetector.Variant.AdoptOpenJdk_J9
      vendor == "albba" -> JdkVersionDetector.Variant.Dragonwell
      vendor == "amzn" -> JdkVersionDetector.Variant.Corretto
      vendor == "bsg" -> JdkVersionDetector.Variant.BiSheng
      vendor == "graal" -> JdkVersionDetector.Variant.GraalVM
      vendor == "graalce" -> JdkVersionDetector.Variant.GraalVMCE
      vendor == "jbr" -> JdkVersionDetector.Variant.JBR
      vendor == "kona" -> JdkVersionDetector.Variant.Kona
      vendor == "librca" -> JdkVersionDetector.Variant.Liberica
      vendor == "ms" -> JdkVersionDetector.Variant.Microsoft
      vendor == "oracle" -> JdkVersionDetector.Variant.Oracle
      vendor == "open" -> JdkVersionDetector.Variant.Oracle
      vendor == "sapmchn" -> JdkVersionDetector.Variant.SapMachine
      vendor == "sem" -> JdkVersionDetector.Variant.Semeru
      vendor == "tem" -> JdkVersionDetector.Variant.Temurin
      vendor == "zulu" -> JdkVersionDetector.Variant.Zulu
      else -> JdkVersionDetector.Variant.Unknown
    }

    // Check vendor
    val variantName = variant.displayName
    return variantName != null && versionString.contains(variantName)
  }

}

public class SdkmanrcConfigurationProvider: ExternalJavaConfigurationProvider<SdkmanReleaseData> {
  override fun getConfigurationFile(project: Project): File = File(project.basePath, ".sdkmanrc")

  override fun getReleaseData(text: String): SdkmanReleaseData? {
    val properties = Properties().apply {
      load(text.byteInputStream())
    }
    val java = properties.getProperty("java") ?: return null
    return SdkmanReleaseData.parse(java)
  }

  override fun matchAgainstSdk(releaseData: SdkmanReleaseData, sdk: Sdk): Boolean {
    val versionString = sdk.versionString ?: return false
    return releaseData.matchVersionString(versionString)
  }

  override fun matchAgainstPath(releaseData: SdkmanReleaseData, path: String): Boolean {
    val info = SdkVersionUtil.getJdkVersionInfo(path) ?: return false
    return releaseData.matchVersionString(info.displayVersionString())
  }
}