// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.hasOnlyUserName
import com.intellij.diagnostic.ITNProxy.ErrorBean
import com.intellij.errorreport.error.NoSuchEAPUserException
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.idea.IdeaLogger
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.Consumer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

/**
 * This is an internal implementation of [ErrorReportSubmitter] which is used to report exceptions in IntelliJ platform
 * and plugins developed by JetBrains to processing at JetBrains.
 *
 * **It's not supposed to be used by third-party plugins.**
 * Third-party plugins need to provide their own implementations of [ErrorReportSubmitter].
 */
@InternalIgnoreDependencyViolation
open class ITNReporter(private val postUrl: String = "https://ea-report.jetbrains.com/trackerRpc/idea/createScr") : ErrorReportSubmitter() {
  private val INTERVAL = 10 * 60 * 1000L  // an interval between exceptions to form a chain, ms

  @Volatile private var previousReport: Pair<Long, Int>? = null  // (timestamp, threadID) of last reported exception

  override fun getReportActionText(): String = DiagnosticBundle.message("error.report.to.jetbrains.action")

  override fun getPrivacyNoticeText(): String =
    if (ErrorReportConfigurable.credentialsFulfilled) DiagnosticBundle.message("error.dialog.notice.named")
    else DiagnosticBundle.message("error.dialog.notice.anonymous")

  override fun getReporterAccount(): String? = ErrorReportConfigurable.userName

  override fun changeReporterAccount(parentComponent: Component) {
    askJBAccountCredentials(parentComponent, null)
  }

  override fun submit(
    events: Array<IdeaLoggingEvent>,
    additionalInfo: String?,
    parentComponent: Component,
    consumer: Consumer<in SubmittedReportInfo>
  ): Boolean {
    val event = events[0]
    val plugin = IdeErrorsDialog.getPlugin(event)
    val errorBean = createReportBean(event, additionalInfo, plugin)
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
    return submit(project, errorBean, parentComponent, postUrl, consumer::consume)
  }

  @ApiStatus.Internal
  suspend fun submitAutomated(event: IdeaLoggingEvent, plugin: IdeaPluginDescriptor?): SubmittedReportInfo {
    val errorBean = createReportBean(event, comment = "Automatically reported exception", plugin)
    return service<ITNProxyCoroutineScopeHolder>().coroutineScope.async {
      try {
        val reportId = ITNProxy.sendError(null, null, errorBean, postUrl)
        updatePreviousReport(event, reportId)
        SubmittedReportInfo(ITNProxy.getBrowseUrl(reportId), reportId.toString(), SubmittedReportInfo.SubmissionStatus.NEW_ISSUE)
      }
      catch (_: Exception) {
        SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED)
      }
    }.await()
  }

  /**
   * Used to enable error reporting even in release versions.
   */
  open fun showErrorInRelease(event: IdeaLoggingEvent): Boolean = false

  private fun createReportBean(event: IdeaLoggingEvent, comment: String?, plugin: IdeaPluginDescriptor?): ErrorBean {
    val lastActionId = IdeaLogger.ourLastActionId
    val prevReport = previousReport
    val eventDate = (event.data as? AbstractMessage)?.date
    val prevReportId = if (prevReport != null && eventDate != null && eventDate.time - prevReport.first in 0..INTERVAL) prevReport.second else -1
    return ErrorBean(event, comment, plugin?.pluginId?.idString, plugin?.name, plugin?.version, lastActionId, prevReportId)
  }

  private fun updatePreviousReport(event: IdeaLoggingEvent, reportId: Int) {
    val eventDate = (event.data as? AbstractMessage)?.date
    previousReport = if (eventDate != null) eventDate.time to reportId else null
  }

  private fun submit(project: Project?, errorBean: ErrorBean, parentComponent: Component, postUrl: String, callback: (SubmittedReportInfo) -> Unit): Boolean {
    val credentialsLazy = suspend {
      var credentials = ErrorReportConfigurable.getCredentials()
      if (credentials.hasOnlyUserName()) {
        credentials = withContext(Dispatchers.EDT) {
          askJBAccountCredentials(parentComponent, project)
        }
      }
      credentials
    }
    submit(project, credentialsLazy, errorBean, parentComponent, postUrl, callback)
    return true
  }

  private fun submit(
    project: Project?,
    credentialsLazy: suspend () -> Credentials?,
    errorBean: ErrorBean,
    parentComponent: Component,
    newThreadPostUrl: String,
    callback: (SubmittedReportInfo) -> Unit
  ) {
    service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch {
      try {
        val credentials = credentialsLazy()
        val reportId = if (project != null) {
          withBackgroundProgress(project, DiagnosticBundle.message("title.submitting.error.report")) {
            ITNProxy.sendError(credentials?.userName, credentials?.getPasswordAsString(), errorBean, newThreadPostUrl)
          }
        }
        else {
          ITNProxy.sendError(credentials?.userName, credentials?.getPasswordAsString(), errorBean, newThreadPostUrl)
        }
        updatePreviousReport(errorBean.event, reportId)
        onSuccess(project, reportId, callback)
      }
      catch (e: Exception) {
        onError(project, e, errorBean, parentComponent, newThreadPostUrl, callback)
      }
    }
  }

  private fun onSuccess(project: Project?, reportId: Int, callback: (SubmittedReportInfo) -> Unit) {
    val reportUrl = ITNProxy.getBrowseUrl(reportId)
    callback(SubmittedReportInfo(reportUrl, reportId.toString(), SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
    val content = DiagnosticBundle.message("error.report.gratitude")
    val title = DiagnosticBundle.message("error.report.submitted")
    Notification("Error Report", title, content, NotificationType.INFORMATION)
      .setImportant(false)
      .addAction(NotificationAction.createSimpleExpiring(DiagnosticBundle.message("error.report.view.action")) { BrowserUtil.browse(reportUrl) })
      .notify(project)
  }

  private suspend fun onError(
    project: Project?,
    e: Exception,
    errorBean: ErrorBean,
    parentComponent: Component,
    newThreadPostUrl: String,
    callback: (SubmittedReportInfo) -> Unit
  ) {
    Logger.getInstance(ITNReporter::class.java).info("reporting failed: ${e}")
    withContext(Dispatchers.EDT) {
      if (e is UpdateAvailableException) {
        val message = DiagnosticBundle.message("error.report.new.build.message", e.message)
        val title = DiagnosticBundle.message("error.report.new.build.title")
        val icon = Messages.getWarningIcon()
        if (parentComponent.isShowing) Messages.showMessageDialog(parentComponent, message, title, icon)
        else Messages.showMessageDialog(project, message, title, icon)
        callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else if (e is NoSuchEAPUserException) {
        val credentials = askJBAccountCredentials(parentComponent, project, true)
        if (credentials != null) {
          submit(project, { credentials }, errorBean, parentComponent, newThreadPostUrl, callback)
        }
        else {
          callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
        }
      }
      else if (e is CancellationException) {
        callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else {
        val message = DiagnosticBundle.message("error.report.failed.message", e.message)
        val title = DiagnosticBundle.message("error.report.failed.title")
        val result = MessageDialogBuilder.yesNo(title, message).ask(project)
        if (!result || !submit(project, errorBean, parentComponent, newThreadPostUrl, callback)) {
          callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
        }
      }
    }
  }
}
