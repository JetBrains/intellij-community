// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.FeedbackDescriptionProvider
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.customization.ExternalProductResourceUrls.Companion.getInstance
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.LicensingFacade
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.awt.GraphicsEnvironment

class SendFeedbackAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val isSupportedOS = SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows
    val feedbackReporter = getInstance().feedbackReporter
    if (feedbackReporter != null && isSupportedOS) {
      val feedbackSite = feedbackReporter.destinationDescription
      e.presentation.setDescription(ActionsBundle.messagePointer("action.SendFeedback.detailed.description", feedbackSite))
      e.presentation.setEnabledAndVisible(true)
    }
    else {
      e.presentation.setEnabledAndVisible(false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val feedbackReporter = getInstance().feedbackReporter
    if (feedbackReporter != null) {
      val formShown = feedbackReporter.showFeedbackForm(e.project, false)
      if (!formShown) {
        submit(e.project)
      }
    }
  }

  companion object {
    @JvmStatic
    fun submit(project: Project?) {
      val projectOrDefaultProject = project ?: ProjectManager.getInstance().defaultProject
      service<ReportFeedbackService>().coroutineScope.launch {
        withBackgroundProgress(projectOrDefaultProject, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
          openFeedbackPageInBrowser(project, getDescription(project))
        }
      }
    }

    @Deprecated("""use {@link FeedbackDescriptionProvider} extension point to provide additional data to description used by the default
    'Send Feedback' action instead of implementing your own action and calling this method.""")
    fun submit(project: Project?, description: String) {
      openFeedbackPageInBrowser(project, description)
    }

    private fun openFeedbackPageInBrowser(project: Project?, description: String) {
      val feedbackReporter = getInstance().feedbackReporter
      if (feedbackReporter == null) return
      BrowserUtil.browse(feedbackReporter.feedbackFormUrl(description).toExternalForm(), project)
    }


    @JvmStatic
    @Deprecated("""use {@link FeedbackDescriptionProvider} extension point to provide additional data to description used by the default 
    'Send Feedback' action instead of implementing your own action and calling this method.""")
    fun submit(project: Project?, urlTemplate: String, description: String) {
      val appInfo = ApplicationInfoEx.getInstanceEx()
      val eap = appInfo.isEAP
      val la = LicensingFacade.getInstance()
      val url = urlTemplate
        .replace("\$BUILD",
                 URLUtil.encodeURIComponent(if (eap) appInfo.getBuild().asStringWithoutProductCode() else appInfo.getBuild().asString()))
        .replace("\$TIMEZONE", URLUtil.encodeURIComponent(System.getProperty("user.timezone", "")))
        .replace("\$VERSION", URLUtil.encodeURIComponent(appInfo.getFullVersion()))
        .replace("\$EVAL", URLUtil.encodeURIComponent(if (la != null && la.isEvaluationLicense) "true" else "false"))
        .replace("\$DESCR", URLUtil.encodeURIComponent(description))
      BrowserUtil.browse(url, project)
    }

    suspend fun getDescription(project: Project?): String {
      val sb: @NonNls StringBuilder = StringBuilder("\n\n")
      sb.append(ApplicationInfoEx.getInstanceEx().getBuild().asString()).append(", ")
      val javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"))
      sb.append("JRE ")
      sb.append(javaVersion)
      val archDataModel = System.getProperty("sun.arch.data.model")
      if (archDataModel != null) {
        sb.append("x").append(archDataModel)
      }
      val javaVendor = System.getProperty("java.vm.vendor")
      if (javaVendor != null) {
        sb.append(" ").append(javaVendor)
      }
      sb.append(", OS ").append(System.getProperty("os.name"))
      val osArch = System.getProperty("os.arch")
      if (osArch != null) {
        sb.append("(").append(osArch).append(")")
      }

      val osVersion = System.getProperty("os.version")
      val osPatchLevel = System.getProperty("sun.os.patch.level")
      if (osVersion != null) {
        sb.append(" v").append(osVersion)
        if (osPatchLevel != null && "unknown" != osPatchLevel) {
          sb.append(" ").append(osPatchLevel)
        }
      }
      if (!GraphicsEnvironment.isHeadless()) {
        sb.append(", screens ")
        val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        for (i in devices.indices) {
          if (i > 0) sb.append(", ")
          val device = devices[i]
          val displayMode = device.getDisplayMode()
          val scale = sysScale(device.defaultConfiguration)
          sb.append(displayMode.width * scale).append("x").append(displayMode.height * scale)
        }
        if (UIUtil.isRetina()) {
          sb.append(if (SystemInfo.isMac) "; Retina" else "; HiDPI")
        }
      }
      for (ext in EP_NAME.extensions) {
        val pluginDescription = ext.getDescription(project)
        if (!pluginDescription.isNullOrEmpty()) {
          sb.append("\n").append(pluginDescription)
        }
      }
      return sb.toString()
    }
    private val EP_NAME = ExtensionPointName<FeedbackDescriptionProvider>("com.intellij.feedbackDescriptionProvider")
  }
}