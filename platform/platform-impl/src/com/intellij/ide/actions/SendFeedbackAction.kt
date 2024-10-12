// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.FeedbackDescriptionProvider
import com.intellij.ide.IdeBundle
import com.intellij.ide.troubleshooting.DisplayInfo
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.GraphicsEnvironment

@ApiStatus.Internal
class SendFeedbackAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val (os, arch) = OS.CURRENT to CpuArch.CURRENT
    val isSupported = (os == OS.Windows || os == OS.macOS || os == OS.Linux) && (arch == CpuArch.X86_64 || arch == CpuArch.ARM64)
    val feedbackReporter = ExternalProductResourceUrls.getInstance().feedbackReporter
    if (feedbackReporter != null && isSupported) {
      e.presentation.setDescription(ActionsBundle.messagePointer("action.SendFeedback.detailed.description", feedbackReporter.destinationDescription))
      e.presentation.setEnabledAndVisible(true)
    }
    else {
      e.presentation.setEnabledAndVisible(false)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ExternalProductResourceUrls.getInstance().feedbackReporter?.let { feedbackReporter ->
      val customFormShown = feedbackReporter.showFeedbackForm(e.project, false)
      if (!customFormShown) {
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

    private fun openFeedbackPageInBrowser(project: Project?, description: String) {
      ExternalProductResourceUrls.getInstance().feedbackReporter?.let { feedbackReporter ->
        BrowserUtil.browse(feedbackReporter.feedbackFormUrl(description).toExternalForm(), project)
      }
    }

    suspend fun getDescription(project: Project?): String {
      val sb = StringBuilder("\n\n")

      sb.append(ApplicationInfoEx.getInstanceEx().getBuild().asString()).append(", ")

      val javaVersion = System.getProperty("java.runtime.version", System.getProperty("java.version", "unknown"))
      sb.append("JRE ").append(javaVersion)
      System.getProperty("sun.arch.data.model")?.let { sb.append('x').append(it) }
      System.getProperty("java.vm.vendor")?.let { sb.append(' ').append(it) }

      sb.append(", OS ").append(System.getProperty("os.name"))
      System.getProperty("os.arch")?.let { sb.append('(').append(it).append(')') }
      System.getProperty("os.version")?.let { osVersion ->
        sb.append(" v").append(osVersion)
        val osPatchLevel = System.getProperty("sun.os.patch.level")
        if (osPatchLevel != null && "unknown" != osPatchLevel) {
          sb.append(' ').append(osPatchLevel)
        }
      }

      if (!GraphicsEnvironment.isHeadless()) {
        sb.append(", screens ").append(
          DisplayInfo.get().screens.joinToString { "${it.resolution} (${it.scaling})" }
        )
        if (UIUtil.isRetina()) {
          sb.append(if (SystemInfo.isMac) "; Retina" else "; HiDPI")
        }
      }

      try {
        EP_NAME.extensionList.forEach { ext ->
          val pluginDescription = ext.getDescription(project)
          if (!pluginDescription.isNullOrEmpty()) {
            sb.append('\n').append(pluginDescription)
          }
        }
      }
      catch (t: Throwable) {
        logger<SendFeedbackAction>().error(t)
      }

      return sb.toString()
    }

    private val EP_NAME = ExtensionPointName<FeedbackDescriptionProvider>("com.intellij.feedbackDescriptionProvider")
  }
}
