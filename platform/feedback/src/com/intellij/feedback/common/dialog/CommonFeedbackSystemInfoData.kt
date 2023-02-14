// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.internal.statistic.utils.platformPlugin
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.LicensingFacade
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/** This number should be increased when [CommonFeedbackSystemInfoData] fields changing */
const val COMMON_FEEDBACK_SYSTEM_INFO_VERSION = 2

@Serializable
data class CommonFeedbackSystemInfoData(
  val osVersion: String,
  private val memorySize: Long, // in megabytes
  val coresNumber: Int,
  val appVersionWithBuild: String,
  private val isEvaluationLicense: Boolean?,
  private val licenseRestrictions: List<String>,
  val runtimeVersion: String,
  private val isInternalModeEnabled: Boolean,
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
        getLicenseEvaluationInfo(),
        getLicenseRestrictionsInfo(),
        getRuntimeVersion(),
        getIsInternalMode(),
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
      appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build", appBuild.asString())
      val timestamp: Date = appInfoEx.buildDate.time
      if (appBuild.isSnapshot) {
        val time = SimpleDateFormat("HH:mm").format(timestamp)
        appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build.date.time",
                                                   NlsMessages.formatDateLong(timestamp), time)
      }
      else {
        appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build.date",
                                                   NlsMessages.formatDateLong(timestamp))
      }
      return appVersion
    }

    private fun getLicenseRestrictionsInfo(): List<String> {
      return LicensingFacade.getInstance()?.licenseRestrictionsMessages ?: emptyList()
    }

    private fun getLicenseEvaluationInfo(): Boolean? {
      return LicensingFacade.getInstance()?.isEvaluationLicense
    }

    private fun getRuntimeVersion() = SystemInfo.JAVA_RUNTIME_VERSION + SystemInfo.OS_ARCH
    private fun getIsInternalMode(): Boolean = ApplicationManager.getApplication().isInternal
    private fun getRegistryKeys(): List<String> = Registry.getAll().stream().filter { value: RegistryValue ->
      val pluginId: String? = value.pluginId
      val pluginInfo = if (pluginId != null) getPluginInfoById(PluginId.getId(pluginId)) else platformPlugin
      value.isChangedFromDefault && pluginInfo.isSafeToReport()
    }.map { v: RegistryValue -> v.key + "=" + v.asString() }.toList()

    private fun getDisabledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isEnabled }

    private fun getNonBundledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isBundled }

    private fun getPluginsNamesWithVersion(filter: (IdeaPluginDescriptor) -> Boolean): List<String> =
      PluginManagerCore.getLoadedPlugins().stream()
        .filter { filter(it) }
        .map { p: IdeaPluginDescriptor ->
          val pluginId = p.pluginId
          val pluginInfo = getPluginInfoById(pluginId)
          if (pluginInfo.isSafeToReport()) {
            pluginId.idString + " (" + p.version + ")"
          }
          else {
            "third.party"
          }
        }
        .toList()
  }

  fun getMemorySizeForDialog() = memorySize.toString() + "M"
  fun getLicenseRestrictionsForDialog() = if (licenseRestrictions.isEmpty())
    CommonFeedbackBundle.message("dialog.feedback.system.info.panel.license.no.info")
  else
    licenseRestrictions.joinToString("\n")

  fun getIsLicenseEvaluationForDialog(): String {
    return when (isEvaluationLicense) {
      true -> "True"
      false -> "False"
      null -> "No Info"
    }
  }

  fun getIsInternalModeForDialog(): String {
    return when (isInternalModeEnabled) {
      true -> "True"
      false -> "False"
    }
  }

  fun getRegistryKeysForDialog(): String {
    val registryKeys: String = registry.joinToString("\n")
    return if (!StringUtil.isEmpty(registryKeys)) {
      registryKeys
    }
    else {
      CommonFeedbackBundle.message("dialog.feedback.system.info.panel.registry.empty")
    }
  }

  fun getDisabledBundledPluginsForDialog(): String {
    val disabledPlugins = disabledBundledPlugins.joinToString("\n")
    return if (!StringUtil.isEmpty(disabledPlugins)) {
      disabledPlugins
    }
    else {
      CommonFeedbackBundle.message("dialog.feedback.system.info.panel.disabled.plugins.empty")
    }
  }

  fun getNonBundledPluginsForDialog(): String {
    val nonBundledPluginsString = nonBundledPlugins.joinToString("\n")
    return if (!StringUtil.isEmpty(nonBundledPluginsString)) {
      nonBundledPluginsString
    }
    else {
      CommonFeedbackBundle.message("dialog.feedback.system.info.panel.nonbundled.plugins.empty")
    }
  }

  override fun toString(): String {
    return buildString {
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.title"))
      appendLine()
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.os.version"))
      appendLine(osVersion)
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.memory"))
      appendLine(getMemorySizeForDialog())
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.cores"))
      appendLine(coresNumber)
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version"))
      appendLine(appVersionWithBuild)
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.license.evaluation"))
      appendLine(getIsLicenseEvaluationForDialog())
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.license.restrictions"))
      appendLine(getLicenseRestrictionsForDialog())
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.runtime.version"))
      appendLine(runtimeVersion)
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.internal.mode.enabled"))
      appendLine(isInternalModeEnabled)
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.registry"))
      appendLine(getRegistryKeysForDialog())
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.disabled.plugins"))
      appendLine(getDisabledBundledPluginsForDialog())
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.nonbundled.plugins"))
      appendLine(getNonBundledPluginsForDialog())
    }
  }
}