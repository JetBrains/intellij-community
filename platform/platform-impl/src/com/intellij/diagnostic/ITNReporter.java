/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.errorreport.bean.ErrorBean
import com.intellij.errorreport.error.InternalEAPException
import com.intellij.errorreport.error.NoSuchEAPUserException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.errorreport.itn.ITNProxy
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.idea.IdeaLogger
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.xml.util.XmlStringUtil
import java.awt.Component
import javax.swing.Icon

private var previousExceptionThreadId = 0

open class ITNReporter : ErrorReportSubmitter() {
  override fun getReportActionText(): String = DiagnosticBundle.message("error.report.to.jetbrains.action")

  override fun submit(events: Array<IdeaLoggingEvent>,
                      additionalInfo: String?,
                      parentComponent: Component,
                      consumer: Consumer<SubmittedReportInfo>): Boolean {
    return submit(events.get(0), parentComponent, consumer, ErrorBean(events.get(0).throwable, IdeaLogger.ourLastActionId), additionalInfo)
  }

  /**
   * Used to enable error reporting even in release versions.
   */
  open fun showErrorInRelease(event: IdeaLoggingEvent) = false
}

fun setPluginInfo(event: IdeaLoggingEvent, errorBean: ErrorBean) {
  val t = event.throwable
  if (t != null) {
    val pluginId = IdeErrorsDialog.findPluginId(t)
    if (pluginId != null) {
      val ideaPluginDescriptor = PluginManager.getPlugin(pluginId)
      if (ideaPluginDescriptor != null && (!ideaPluginDescriptor.isBundled || ideaPluginDescriptor.allowBundledUpdate())) {
        errorBean.pluginName = ideaPluginDescriptor.name
        errorBean.pluginVersion = ideaPluginDescriptor.version
      }
    }
  }
}

private fun updatePreviousThreadId(threadId: Int?) {
  previousExceptionThreadId = threadId!!
}

private fun showMessageDialog(parentComponent: Component, project: Project?, message: String, title: String, icon: Icon) {
  if (parentComponent.isShowing) {
    Messages.showMessageDialog(parentComponent, message, title, icon)
  }
  else {
    Messages.showMessageDialog(project, message, title, icon)
  }
}

@Messages.YesNoResult
private fun showYesNoDialog(parentComponent: Component, project: Project?, message: String, title: String, icon: Icon): Int {
  if (parentComponent.isShowing) {
    return Messages.showYesNoDialog(parentComponent, message, title, icon)
  }
  else {
    return Messages.showYesNoDialog(project, message, title, icon)
  }
}

private fun submit(event: IdeaLoggingEvent, parentComponent: Component, callback: Consumer<SubmittedReportInfo>, errorBean: ErrorBean, description: String?): Boolean {
  var credentials = ErrorReportConfigurable.getCredentials()
  // ask password only if user name was specified
  if (credentials?.userName != null && credentials?.password.isNullOrEmpty()) {
    if (!JetBrainsAccountDialog(parentComponent).showAndGet()) {
      return false
    }

    credentials = ErrorReportConfigurable.getCredentials()
  }

  errorBean.description = description
  errorBean.message = event.message

  if (previousExceptionThreadId != 0) {
    errorBean.previousException = previousExceptionThreadId
  }

  setPluginInfo(event, errorBean)

  val data = event.data
  if (data is AbstractMessage) {
    errorBean.assigneeId = data.assigneeId
    errorBean.attachments = data.includedAttachments
  }

  var login = credentials?.userName
  var password = credentials?.getPasswordAsString()
  if (login.isNullOrBlank() && password.isNullOrBlank()) {
    login = "idea_anonymous"
    password = "guest"
  }

  val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
  ITNProxy.sendError(project, login, password, errorBean, { threadId ->
    updatePreviousThreadId(threadId)
    val url = ITNProxy.getBrowseUrl(threadId!!)
    val linkText = threadId.toString()
    val reportInfo = SubmittedReportInfo(url, linkText, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE)
    callback.consume(reportInfo)
    ApplicationManager.getApplication().invokeLater {
      val text = StringBuilder()
      IdeErrorsDialog.appendSubmissionInformation(reportInfo, text)
      text.append('.').append("<br/>").append(DiagnosticBundle.message("error.report.gratitude"))
      val content = XmlStringUtil.wrapInHtml(text)
      ReportMessages.GROUP.createNotification(ReportMessages.ERROR_REPORT, content, NotificationType.INFORMATION,
          NotificationListener.URL_OPENING_LISTENER).setImportant(false).notify(project)
    }
  }) { e ->
    Logger.getInstance(ITNReporter::class.java).info("reporting failed: " + e)
    ApplicationManager.getApplication().invokeLater {
      val msg: String
      if (e is NoSuchEAPUserException) {
        msg = DiagnosticBundle.message("error.report.authentication.failed")
      }
      else if (e is InternalEAPException) {
        msg = DiagnosticBundle.message("error.report.posting.failed", e.message)
      }
      else {
        msg = DiagnosticBundle.message("error.report.sending.failure")
      }
      if (e is UpdateAvailableException) {
        val message = DiagnosticBundle.message("error.report.new.eap.build.message", e.message)
        showMessageDialog(parentComponent, project, message, CommonBundle.getWarningTitle(), Messages.getWarningIcon())
        callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else if (showYesNoDialog(parentComponent, project, msg, ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != Messages.YES) {
        callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else {
        if (e is NoSuchEAPUserException) {
          val dialog: JetBrainsAccountDialog
          if (parentComponent.isShowing) {
            dialog = JetBrainsAccountDialog(parentComponent)
          }
          else {
            dialog = JetBrainsAccountDialog(project!!)
          }
          dialog.show()
        }
        ApplicationManager.getApplication().invokeLater { submit(event, parentComponent, callback, errorBean, description) }
      }
    }
  }
  return true
}
