// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.hasOnlyUserName
import com.intellij.credentialStore.isFulfilled
import com.intellij.diagnostic.ITNProxy.ErrorBean
import com.intellij.errorreport.error.NoSuchEAPUserException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.DataManager
import com.intellij.idea.IdeaLogger
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.xml.util.XmlStringUtil
import java.awt.Component

private const val INTERVAL = 10 * 60 * 1000L  // an interval between exceptions to form a chain, ms
@Volatile private var previousReport: Pair<Long, Int>? = null  // (timestamp, threadID) of last reported exception

/**
 * This is an internal implementation of [ErrorReportSubmitter] which is used to report exceptions in IntelliJ platform and plugins developed
 * by JetBrains to processing at JetBrains. **It isn't supposed to be used by third-party plugins.** Third-party plugins need to provide
 * their own implementations of [ErrorReportSubmitter].
 */
open class ITNReporter : ErrorReportSubmitter() {
  override fun getReportActionText(): String = DiagnosticBundle.message("error.report.to.jetbrains.action")

  override fun getPrivacyNoticeText(): String =
    if (ErrorReportConfigurable.getCredentials().isFulfilled()) DiagnosticBundle.message("error.dialog.notice.named")
    else DiagnosticBundle.message("error.dialog.notice.anonymous")

  override fun getReporterAccount(): String? = ErrorReportConfigurable.getCredentials()?.userName ?: ""

  override fun changeReporterAccount(parentComponent: Component) {
    askJBAccountCredentials(parentComponent, null)
  }

  override fun submit(events: Array<IdeaLoggingEvent>,
                      additionalInfo: String?,
                      parentComponent: Component,
                      consumer: Consumer<in SubmittedReportInfo>): Boolean {
    val event = events[0]

    val plugin = IdeErrorsDialog.getPlugin(event)

    val lastActionId = IdeaLogger.ourLastActionId

    var previousReportId = -1
    val previousException = previousReport
    val eventData = event.data
    if (previousException != null && eventData is AbstractMessage && eventData.date.time - previousException.first in 0..INTERVAL) {
      previousReportId = previousException.second
    }

    val errorBean = ErrorBean(event, additionalInfo, plugin?.pluginId?.idString, plugin?.name, plugin?.version, lastActionId, previousReportId)

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))

    return submit(project, errorBean, parentComponent, consumer)
  }

  /**
   * Used to enable error reporting even in release versions.
   */
  open fun showErrorInRelease(event: IdeaLoggingEvent): Boolean = false
}

private fun submit(project: Project?, errorBean: ErrorBean, parentComponent: Component, callback: Consumer<in SubmittedReportInfo>): Boolean {
  var credentials = ErrorReportConfigurable.getCredentials()
  if (credentials.hasOnlyUserName()) {
    credentials = askJBAccountCredentials(parentComponent, project)
    if (credentials == null) return false
  }
  submit(project, credentials, errorBean, parentComponent, callback)
  return true
}

private fun submit(project: Project?,
                   credentials: Credentials?,
                   errorBean: ErrorBean,
                   parentComponent: Component,
                   callback: Consumer<in SubmittedReportInfo>) {
  ITNProxy.sendError(project, credentials?.userName, credentials?.getPasswordAsString(), errorBean,
                     { threadId -> onSuccess(project, threadId, errorBean.event.data, callback) },
                     { e -> onError(project, e, errorBean, parentComponent, callback) })
}

private fun onSuccess(project: Project?, threadId: Int, eventData: Any?, callback: Consumer<in SubmittedReportInfo>) {
  previousReport = if (eventData is AbstractMessage) eventData.date.time to threadId else null

  val linkText = threadId.toString()
  val reportInfo = SubmittedReportInfo(ITNProxy.getBrowseUrl(threadId), linkText, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE)
  callback.consume(reportInfo)
  ApplicationManager.getApplication().invokeLater {
    val text = StringBuilder()
    IdeErrorsDialog.appendSubmissionInformation(reportInfo, text)
    text.append('.').append("<br/>").append(DiagnosticBundle.message("error.report.gratitude"))
    val content = XmlStringUtil.wrapInHtml(text)
    val title = DiagnosticBundle.message("error.report.submitted")
    NotificationGroupManager.getInstance().getNotificationGroup("Error Report")
      .createNotification(title, content, NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER)
      .setImportant(false)
      .notify(project)
  }
}

private fun onError(project: Project?,
                    e: Exception,
                    errorBean: ErrorBean,
                    parentComponent: Component,
                    callback: Consumer<in SubmittedReportInfo>) {
  Logger.getInstance(ITNReporter::class.java).info("reporting failed: $e")
  ApplicationManager.getApplication().invokeLater {
    if (e is UpdateAvailableException) {
      val message = DiagnosticBundle.message("error.report.new.build.message", e.message)
      val title = DiagnosticBundle.message("error.report.new.build.title")
      val icon = Messages.getWarningIcon()
      if (parentComponent.isShowing) Messages.showMessageDialog(parentComponent, message, title, icon)
                                else Messages.showMessageDialog(project, message, title, icon)
      callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
    }
    else if (e is NoSuchEAPUserException) {
      val credentials = askJBAccountCredentials(parentComponent, project, true)
      if (credentials != null) {
        submit(project, credentials, errorBean, parentComponent, callback)
      }
      else {
        callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
    }
    else {
      val message = DiagnosticBundle.message("error.report.failed.message", e.message)
      val title = DiagnosticBundle.message("error.report.failed.title")
      val result = MessageDialogBuilder.yesNo(title, message).ask(project)
      if (!result || !submit(project, errorBean, parentComponent, callback)) {
        callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
    }
  }
}
