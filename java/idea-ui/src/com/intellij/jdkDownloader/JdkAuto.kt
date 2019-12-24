// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.UnknownSdkResolver
import com.intellij.openapi.projectRoots.impl.UnknownSdkResolver.*
import com.intellij.openapi.roots.ui.configuration.SdkDetector
import com.intellij.openapi.roots.ui.configuration.SdkDetector.DetectedSdkListener
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector

class JdkAuto : UnknownSdkResolver, JdkDownloaderBase {
  private val LOG = logger<JdkAuto>()

  override fun createResolver(project: Project, indicator: ProgressIndicator): UnknownSdkLookup? {
    if (!Registry.`is`("jdk.auto.setup")) return null
    if (ApplicationManager.getApplication().isUnitTestMode) return null

    return object : UnknownSdkLookup {
      val sdkType = JavaSdk.getInstance()

      val lazyDownloadModel by lazy {
        indicator.text = "Downloading JDK list..."
        JdkListDownloader.downloadModelForJdkInstaller(indicator)
      }

      override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator): DownloadSdkFix? {
        if (sdk.sdkType != sdkType) return null

        val req = JdkRequirements.parseRequirement(sdk.sdkName) ?: return null
        LOG.info("Looking for a possible download for ${sdk.sdkType.presentableName} with name ${sdk.sdkName}")

        //we select the newest matching version for a possible fix
        val jdkToDownload = lazyDownloadModel
                              .filter { req.matches(it) }
                              .mapNotNull {
                                val v = JavaVersion.tryParse(it.versionString)
                                if (v != null) {
                                  it to v
                                }
                                else null
                              }.maxBy { it.second }
                              ?.first ?: return null

        return object: DownloadSdkFix {
          override fun getDownloadDescription() = jdkToDownload.fullPresentationText

          override fun createTask(indicator: ProgressIndicator): SdkDownloadTask {
            val homeDir = JdkInstaller.defaultInstallDir(jdkToDownload)
            val request = JdkInstaller.prepareJdkInstallation(jdkToDownload, homeDir)
            return newDownloadTask(request)
          }
        }
      }

      val lazyLocalJdks by lazy {
        indicator.text = "Detecting local JDKs..."
        val result = mutableListOf<JavaLocalSdkFix>()

        SdkDetector.getInstance().detectSdks(sdkType, indicator, object : DetectedSdkListener {
          override fun onSdkDetected(type: SdkType, version: String, home: String) {
            val javaVersion = JavaVersion.tryParse(version)
            if (javaVersion != null) {
              result += JavaLocalSdkFix(home, javaVersion)
            }
          }
        })

        result
      }

      override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator): LocalSdkFix? {
        if (sdk.sdkType != sdkType) return null

        val req = JdkRequirements.parseRequirement(sdk.sdkName) ?: return null
        LOG.info("Looking for a local SDK for ${sdk.sdkType.presentableName} with name ${sdk.sdkName}")

        fun List<JavaLocalSdkFix>.pickBestMatch() = this.minBy { it.version }

        return tryUsingExistingSdk(req, sdk.sdkType, indicator).pickBestMatch()
               ?: lazyLocalJdks.filter { req.matches(it.versionString) }.pickBestMatch()
      }

      private fun tryUsingExistingSdk(req: JdkRequirement, sdkType: SdkType, indicator: ProgressIndicator): List<JavaLocalSdkFix> {
        indicator.text = "Checking existing SDKs..."
        return runReadAction { ProjectJdkTable.getInstance().allJdks }
          .filter { it.sdkType == sdkType }
          .filter { runCatching { req.matches(it) }.getOrNull() == true }
          .filter { runCatching { sdkType.isValidSdkHome(it.homePath) }.getOrNull() == true }
          .filter { runCatching { it.versionString != null }.getOrNull() == true }
          .mapNotNull {
            val homeDir = it.homePath ?: return@mapNotNull null
            val versionString = it.versionString ?: return@mapNotNull null
            val version = JavaVersion.tryParse(versionString) ?: return@mapNotNull null
            JavaLocalSdkFix(homeDir, version)
          }
      }
    }
  }

  private class JavaLocalSdkFix(val homeDir: String,
                                val version: JavaVersion) : LocalSdkFix {
    override fun getExistingSdkHome() = homeDir
    override fun getVersionString() = JdkVersionDetector.formatVersionString(version)
  }
}
