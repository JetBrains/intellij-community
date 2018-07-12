// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.credentialStore.hasOnlyUserName
import com.intellij.credentialStore.isFulfilled
import com.intellij.diagnostic.ITNProxy.ErrorBean
import com.intellij.errorreport.error.InternalEAPException
import com.intellij.errorreport.error.NoSuchEAPUserException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.DataManager
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
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.xml.util.XmlStringUtil
import java.awt.Component
import java.lang.Exception
import javax.swing.Icon

private const val INTERVAL = 10 * 60 * 1000L  // an interval between exceptions to form a chain, ms
@Volatile private var previousReport: Pair<Long, Int>? = null  // (timestamp, threadID) of last reported exception

open class ITNReporter : ErrorReportSubmitter() {
  override fun getReportActionText(): String = DiagnosticBundle.message("error.report.to.jetbrains.action")

  override fun getPrivacyNoticeText(): String =
    if (ErrorReportConfigurable.getCredentials().isFulfilled()) DiagnosticBundle.message("error.dialog.notice.named")
    else DiagnosticBundle.message("error.dialog.notice.anonymous")

  override fun submit(events: Array<IdeaLoggingEvent>,
                      additionalInfo: String?,
                      parentComponent: Component,
                      consumer: Consumer<SubmittedReportInfo>): Boolean {
    val event = events[0]

    val pluginInfo = IdeErrorsDialog.getPluginInfo(event)

    val lastActionId = IdeaLogger.ourLastActionId

    var previousReportId = -1
    val previousException = previousReport
    val eventData = event.data
    if (previousException != null && eventData is AbstractMessage && eventData.date.time - previousException.first in 0..INTERVAL) {
      previousReportId = previousException.second
    }

    val errorBean = ErrorBean(event, additionalInfo, pluginInfo?.first, pluginInfo?.second, lastActionId, previousReportId)

    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))

    return submit(errorBean, parentComponent, consumer, project)
  }

  /**
   * Used to enable error reporting even in release versions.
   */
  open fun showErrorInRelease(event: IdeaLoggingEvent): Boolean = false
}

/** @deprecated use [IdeErrorsDialog.getPluginInfo] (to be removed in IDEA 2019) */
@Suppress("unused", "DEPRECATION")
fun setPluginInfo(event: IdeaLoggingEvent, errorBean: com.intellij.errorreport.bean.ErrorBean) {
  val pluginInfo = IdeErrorsDialog.getPluginInfo(event)
  if (pluginInfo != null) {
    errorBean.pluginName = pluginInfo.first
    errorBean.pluginVersion = pluginInfo.second
  }
}

private fun showMessageDialog(parentComponent: Component, project: Project?, message: String, title: String, icon: Icon) {
  if (parentComponent.isShowing) {
    Messages.showMessageDialog(parentComponent, message, title, icon)
  }
  else {
    Messages.showMessageDialog(project, message, title, icon)
  }
}

private fun submit(errorBean: ErrorBean, parentComponent: Component, callback: Consumer<SubmittedReportInfo>, project: Project?): Boolean {
  var credentials = ErrorReportConfigurable.getCredentials()
  if (credentials.hasOnlyUserName()) {
    // ask password only if user name was specified
    if (!showJetBrainsAccountDialog(parentComponent).showAndGet()) {
      return false
    }
    credentials = ErrorReportConfigurable.getCredentials()
  }

  ITNProxy.sendError(project, credentials?.userName, credentials?.getPasswordAsString(), errorBean,
                     { threadId -> onSuccess(threadId, errorBean.event.data, callback, project) },
                     { e -> onError(e, errorBean, parentComponent, callback, project) })
  return true
}

private fun onSuccess(threadId: Int, eventData: Any?, callback: Consumer<SubmittedReportInfo>, project: Project?) {
  previousReport = if (eventData is AbstractMessage) eventData.date.time to threadId else null

  val linkText = threadId.toString()
  val reportInfo = SubmittedReportInfo(ITNProxy.getBrowseUrl(threadId), linkText, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE)
  callback.consume(reportInfo)
  ApplicationManager.getApplication().invokeLater {
    val text = StringBuilder()
    IdeErrorsDialog.appendSubmissionInformation(reportInfo, text)
    text.append('.').append("<br/>").append(DiagnosticBundle.message("error.report.gratitude"))
    val content = XmlStringUtil.wrapInHtml(text)
    ReportMessages.GROUP
      .createNotification(ReportMessages.ERROR_REPORT, content, NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER)
      .setImportant(false)
      .notify(project)
  }
}

private fun onError(e: Exception, errorBean: ErrorBean, parentComponent: Component, callback: Consumer<SubmittedReportInfo>, project: Project?) {
  Logger.getInstance(ITNReporter::class.java).info("reporting failed: $e")
  ApplicationManager.getApplication().invokeLater {
    if (e is UpdateAvailableException) {
      val message = DiagnosticBundle.message("error.report.new.eap.build.message", e.message)
      showMessageDialog(parentComponent, project, message, CommonBundle.getWarningTitle(), Messages.getWarningIcon())
      callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      return@invokeLater
    }

    val msg = when (e) {
      is NoSuchEAPUserException -> DiagnosticBundle.message("error.report.authentication.failed")
      is InternalEAPException -> DiagnosticBundle.message("error.report.posting.failed", e.message)
      else -> DiagnosticBundle.message("error.report.sending.failure")
    }
    if (!MessageDialogBuilder.yesNo(ReportMessages.ERROR_REPORT, msg).project(project).isYes) {
      callback.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
    }
    else {
      if (e is NoSuchEAPUserException) {
        showJetBrainsAccountDialog(parentComponent, project).show()
      }
      ApplicationManager.getApplication().invokeLater { submit(errorBean, parentComponent, callback, project) }
    }
  }
}