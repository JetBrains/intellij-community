// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.LicensingFacade
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.streams.toList

@Serializable
data class CommonFeedbackSystemInfoData(
  val osVersion: String,
  private val memorySize: Long, // in megabytes
  val coresNumber: Int,
  val appVersionWithBuild: String,
  private val licenseInfo: List<String>,
  val runtimeVersion: String,
  private val registry: List<String>,
  private val disabledBundledPlugins: List<String>,
  private val nonBundledPlugins: List<String>
) {
  companion object {
    fun getCurrentData(): CommonFeedbackSystemInfoData {
      return CommonFeedbackSystemInfoData(
        getOsVersion(),
        getMemorySize(),
        getCoresNumber(),
        getAppVersionWithBuild(),
        getLicenseInfo(),
        getRuntimeVersion(),
        getRegistryKeys(),
        getDisabledPlugins(),
        getNonBundledPlugins()
      )
    }

    private fun getOsVersion() = SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION
    private fun getMemorySize() = Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE
    private fun getCoresNumber() = Runtime.getRuntime().availableProcessors()
    private fun getAppVersionWithBuild(): String {
      val appInfoEx = ApplicationInfoEx.getInstanceEx()

      var appVersion: String = appInfoEx.fullApplicationName
      val edition = ApplicationNamesInfo.getInstance().editionName
      if (edition != null) {
        appVersion += " ($edition)"
      }
      val appBuild = appInfoEx.build
      appVersion += FeedbackBundle.message("dialog.created.project.system.info.panel.app.version.build", appBuild.asString())
      val timestamp: Date = appInfoEx.buildDate.time
      if (appBuild.isSnapshot) {
        val time = SimpleDateFormat("HH:mm").format(timestamp)
        appVersion += FeedbackBundle.message("dialog.created.project.system.info.panel.app.version.build.date.time",
                                             NlsMessages.formatDateLong(timestamp), time)
      }
      else {
        appVersion += FeedbackBundle.message("dialog.created.project.system.info.panel.app.version.build.date",
                                             NlsMessages.formatDateLong(timestamp))
      }
      return appVersion
    }

    private fun getLicenseInfo(): List<String> {
      val licensingFacade = LicensingFacade.getInstance()
      return if (licensingFacade != null) {
        val licenseInfoList = ArrayList<String>()
        val licensedTo = licensingFacade.licensedToMessage
        if (licensedTo != null) {
          licenseInfoList.add(licensedTo)
        }
        licenseInfoList.addAll(licensingFacade.licenseRestrictionsMessages)
        licenseInfoList
      }
      else {
        emptyList()
      }
    }

    private fun getRuntimeVersion() = SystemInfo.JAVA_RUNTIME_VERSION + SystemInfo.OS_ARCH
    private fun getRegistryKeys(): List<String> = Registry.getAll().stream().filter { obj: RegistryValue -> obj.isChangedFromDefault }
      .map { v: RegistryValue -> v.key + "=" + v.asString() }.toList()


    private fun getDisabledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isEnabled }

    private fun getNonBundledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isBundled }

    private fun getPluginsNamesWithVersion(filter: (IdeaPluginDescriptor) -> Boolean): List<String> =
      PluginManagerCore.getLoadedPlugins().stream()
        .filter { filter(it) }
        .map { p: IdeaPluginDescriptor -> p.pluginId.idString + " (" + p.version + ")" }
        .toList()
  }

  fun getMemorySizeForDialog() = memorySize.toString() + "M"
  fun getLicenseInfoForDialog() = if (licenseInfo.isEmpty())
    FeedbackBundle.message("dialog.created.project.system.info.panel.license.no.info")
  else
    licenseInfo.joinToString("\n")

  fun getRegistryKeysForDialog(): String {
    val registryKeys: String = registry.joinToString("\n")
    return if (!StringUtil.isEmpty(registryKeys)) {
      registryKeys
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.registry.empty")
    }
  }

  fun getDisabledBundledPluginsForDialog(): String {
    val disabledPlugins = disabledBundledPlugins.joinToString("\n")
    return if (!StringUtil.isEmpty(disabledPlugins)) {
      disabledPlugins
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.disabled.plugins.empty")
    }
  }

  fun getNonBundledPluginsForDialog(): String {
    val nonBundledPluginsString = nonBundledPlugins.joinToString("\n")
    return if (!StringUtil.isEmpty(nonBundledPluginsString)) {
      nonBundledPluginsString
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.nonbundled.plugins.empty")
    }
  }

}