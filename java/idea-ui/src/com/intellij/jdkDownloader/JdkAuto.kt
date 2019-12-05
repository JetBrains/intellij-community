// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkDownloader

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkUsagesCollector
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.lang.JavaVersion

class JdkAuto(
  private val project: Project
) {
  private val LOG = logger<JdkAuto>()

  fun lookupFor(sdkTypeName: String, name: String) {
    //filter incompatible SdkTypes
    val sdkType = SdkType.getAllTypes().first { it.name == sdkTypeName }
    if (sdkType !is JavaSdkType) return

    //only user creatable SDKs are possible here
    if (!sdkType.allowCreationByUser()) return

    // make sure SDK does not exists
    if (runReadAction { ProjectJdkTable.getInstance().findJdk(name, sdkTypeName) } != null) return

    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Configuring JDKs", true, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          val req = JdkRequirements.parseRequirement(name) ?: return
          LOG.info("Automatically configuring ${sdkType.presentableName} with name $name")

          indicator.text = "Searching for an JDK for $name..."
          val selected = sequence{
            yield(tryUsingExistingSdk(req, sdkType))
            yield(tryUsingDetectedSdk(req, sdkType))
            yield(tryDownloadNewJdk(req, indicator))
          }.mapNotNull { it.minWith(Comparator { o1, o2 -> o1.version.compareTo(o2.version) }) }
           .firstOrNull()

          if (selected == null) {
            LOG.info("Automatic configuring of ${sdkType.presentableName} with name $name failed")
            return
          }

          LOG.info("Automatic configuring of ${sdkType.presentableName} with name $name selected $selected")

          indicator.text = "Preparing $name..."
          selected.prepare(indicator)

          val newSdk = ProjectJdkTable.getInstance().createSdk(name, sdkType)

          newSdk.sdkModificator.apply {
            this.homePath = selected.homeDir
            this.versionString = selected.version.toString()
          }.commitChanges()

          sdkType.setupSdkPaths(newSdk)
          ProjectJdkTable.getInstance().addJdk(newSdk)
        }
      }
    )
  }

  private open class InstallableSdk(val homeDir: String,
                                    val version: JavaVersion) {
    open fun prepare(indicator: ProgressIndicator?) { }
  }

  private fun tryUsingExistingSdk(req: JdkRequirement, sdkType: SdkType): List<InstallableSdk> {
    return runReadAction { ProjectJdkTable.getInstance().allJdks }
      .filter { it.sdkType == sdkType }
      .filter { runCatching { req.matches(it) }.getOrNull() == true }
      .filter { runCatching { sdkType.isValidSdkHome(it.homePath) }.getOrNull() == true }
      .filter { runCatching { it.versionString != null }.getOrNull() == true }
      .mapNotNull {
        val homeDir = it.homePath ?: return@mapNotNull null
        val versionString = it.versionString ?: return@mapNotNull null
        val version = JavaVersion.tryParse(versionString) ?: return@mapNotNull null
        InstallableSdk(homeDir, version)
      }
  }

  private fun tryUsingDetectedSdk(req: JdkRequirement, sdkType: SdkType): List<InstallableSdk> {
    val result = mutableListOf<InstallableSdk>()
    for (homePath in sdkType.suggestHomePaths()) {

      val versionString = runCatching {
        sdkType.getVersionString(homePath)
      }.getOrNull() ?: continue

      if (!req.matches(versionString)) continue
      val version = JavaVersion.tryParse(versionString) ?: continue

      result += InstallableSdk(homePath, version)
    }

    return result
  }

  private fun tryDownloadNewJdk(req: JdkRequirement, indicator: ProgressIndicator?): List<InstallableSdk> {
    return JdkListDownloader.downloadModel(indicator)
      .filter { req.matches(it) }
      .mapNotNull { jdkToDownload ->
        val homeDir = JdkInstaller.defaultInstallDir(jdkToDownload)
        val version = JavaVersion.tryParse(jdkToDownload.versionString) ?: return@mapNotNull null

        object: InstallableSdk(homeDir, version) {
          override fun prepare(indicator: ProgressIndicator?) {
            val request = JdkInstaller.prepareJdkInstallation(jdkToDownload, targetPath = homeDir)
            JdkInstaller.installJdk(request, indicator)
          }
        }
      }
  }
}
