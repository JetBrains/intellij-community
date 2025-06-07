// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import com.intellij.frontend.HostIdeInfoService
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.internal.statistic.utils.platformPlugin
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.LicensingFacade
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.annotations.Nls
import java.text.SimpleDateFormat
import java.util.*

/** This number should be increased when [CommonFeedbackSystemData] fields changing */
const val COMMON_FEEDBACK_SYSTEM_INFO_VERSION: Int = 3

@Serializable
data class CommonFeedbackSystemData(
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
  private val nonBundledPlugins: List<String>,
  private val isRemoteDevelopmentHost: Boolean,
) : SystemDataJsonSerializable {
  companion object {
    fun getCurrentData(): CommonFeedbackSystemData {
      return CommonFeedbackSystemData(
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
        getNonBundledPlugins(),
        AppMode.isRemoteDevHost()
      )
    }

    private fun getOsVersion() = SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION
    private fun getMemorySize() = Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE
    private fun getCoresNumber() = Runtime.getRuntime().availableProcessors()

    private fun getAppVersionWithBuild(): String {
      val appInfo = ApplicationInfo.getInstance()

      var appVersion: String = appInfo.fullApplicationName
      val edition = ApplicationNamesInfo.getInstance().editionName
      if (edition != null) {
        appVersion += " ($edition)"
      }

      val appBuild = appInfo.build
      appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build", appBuild.asString())
      val timestamp: Date = appInfo.buildDate.time
      if (appBuild.isSnapshot) {
        val time = SimpleDateFormat("HH:mm").format(timestamp)
        appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build.date.time",
                                                   NlsMessages.formatDateLong(timestamp), time)
      }
      else {
        appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.build.date",
                                                   NlsMessages.formatDateLong(timestamp))
      }

      if (appInfo.build.productCode == "JBC") {
        val hostInfo = service<HostIdeInfoService>().getHostInfo()
        if (hostInfo != null) {
          appVersion += CommonFeedbackBundle.message("dialog.feedback.system.info.panel.app.version.host", hostInfo.productCode)
        }
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

    private fun getRegistryKeys(): List<String> {
      return Registry.getAll()
        .filter { value: RegistryValue ->
          val pluginId: String? = value.pluginId
          val pluginInfo = if (pluginId != null) getPluginInfoById(PluginId.getId(pluginId)) else platformPlugin
          value.isChangedFromDefault() && pluginInfo.isSafeToReport()
        }
        .map { v: RegistryValue -> v.key + "=" + v.asString() }
        .toList()
    }

    private fun getDisabledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isEnabled }

    private fun getNonBundledPlugins(): List<String> = getPluginsNamesWithVersion { p: IdeaPluginDescriptor -> !p.isBundled }

    private fun getPluginsNamesWithVersion(filter: (IdeaPluginDescriptor) -> Boolean): List<String> =
      PluginManagerCore.loadedPlugins
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

  fun getMemorySizeForDialog(): String = memorySize.toString() + "M"

  @Suppress("HardCodedStringLiteral")
  fun getLicenseRestrictionsForDialog(): @Nls String = if (licenseRestrictions.isEmpty())
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

  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
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
      appendLine(CommonFeedbackBundle.message("dialog.feedback.system.info.panel.remote.dev.host"))
      appendLine(isRemoteDevelopmentHost.toString())
    }
  }
}