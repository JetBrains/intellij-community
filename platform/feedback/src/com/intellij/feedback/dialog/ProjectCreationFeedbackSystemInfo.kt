// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfo.JAVA_RUNTIME_VERSION
import com.intellij.openapi.util.SystemInfo.OS_ARCH
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.LicensingFacade
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors
import javax.swing.Action
import javax.swing.JComponent

class ProjectCreationFeedbackSystemInfo(
  project: Project?,
  private val createdProjectTypeName: String) : DialogWrapper(project) {

  init {
    init()
    title = FeedbackBundle.message("dialog.created.project.system.info.title")
  }

  override fun createCenterPanel(): JComponent {
    val infoPanel = panel {
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.project.type"))
        }
        cell {
          label(createdProjectTypeName) //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.os.version"))
        }
        cell {
          label(SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION) //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.memory"))
        }
        cell {
          label((Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE).toString() + "M") //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.cores"))
        }
        cell {
          label(Runtime.getRuntime().availableProcessors().toString())
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.app.version"))
        }
        cell {
          MultiLineLabel(getAppVersionWithBuild())() //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.license"))
        }
        cell {
          MultiLineLabel(getLicenseInfo())() //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.runtime.version"))
        }
        cell {
          label(JAVA_RUNTIME_VERSION + OS_ARCH) //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.registry"))
        }
        cell {
          MultiLineLabel(getRegistryKeys())() //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.disabled.plugins"))
        }
        cell {
          MultiLineLabel(getDisabledPlugins())() //NON-NLS
        }
      }
      row {
        cell {
          label(FeedbackBundle.message("dialog.created.project.system.info.panel.nonbundled.plugins"))
        }
        cell {
          MultiLineLabel(getNonBundledPlugins())() //NON-NLS
        }
        largeGapAfter()
      }
    }.also {
      it.border = JBEmptyBorder(10, 10, 10, 10)
    }

    return JBScrollPane(infoPanel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBEmptyBorder(0)
    }
  }

  private fun getAppVersionWithBuild(): String {
    val appInfoEx = ApplicationInfoEx.getInstanceEx()

    var appVersion: String = appInfoEx.fullApplicationName
    val edition = ApplicationNamesInfo.getInstance().editionName
    if (edition != null) {
      appVersion += " ($edition)"
    }
    val appBuild = appInfoEx.build
    appVersion += " " + FeedbackBundle.message("dialog.created.project.system.info.panel.app.version.build", appBuild.asString())
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

  private fun getLicenseInfo(): String {
    val licensingFacade = LicensingFacade.getInstance()
    return if (licensingFacade != null) {
      val licenseInfoList = ArrayList<String>()
      val licensedTo = licensingFacade.licensedToMessage
      if (licensedTo != null) {
        licenseInfoList.add(licensedTo)
      }
      licenseInfoList.addAll(licensingFacade.licenseRestrictionsMessages)
      licenseInfoList.joinToString("\n")
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.license.no.info")
    }
  }

  private fun getRegistryKeys(): String {
    val registryKeys: String = Registry.getAll().stream().filter { obj: RegistryValue -> obj.isChangedFromDefault }
      .map { v: RegistryValue -> v.key + "=" + v.asString() }.collect(Collectors.joining("\n"))
    val registryKeysValue = if (!StringUtil.isEmpty(registryKeys)) {
      registryKeys
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.registry.empty")
    }
    return registryKeysValue
  }

  private fun getDisabledPlugins(): String {
    return getPluginsNamesWithVersion { p: IdeaPluginDescriptor ->
      !p.isEnabled
    }
  }

  private fun getNonBundledPlugins(): String {
    return getPluginsNamesWithVersion { p: IdeaPluginDescriptor ->
      !p.isBundled
    }
  }

  private fun getPluginsNamesWithVersion(filter: (IdeaPluginDescriptor) -> Boolean): String {
    val nonBundledPlugins: String = PluginManagerCore.getLoadedPlugins().stream()
      .filter { filter(it) }
      .map { p: IdeaPluginDescriptor -> p.pluginId.idString + " (" + p.version + ")" }
      .collect(Collectors.joining("\n"))
    val nonBundledPluginsValue = if (!StringUtil.isEmpty(nonBundledPlugins)) {
      nonBundledPlugins
    }
    else {
      FeedbackBundle.message("dialog.created.project.system.info.panel.nonbundled.plugins.empty")
    }
    return nonBundledPluginsValue
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
  }
}