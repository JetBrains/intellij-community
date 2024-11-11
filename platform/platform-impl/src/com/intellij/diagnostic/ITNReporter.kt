// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.ITNProxy.ErrorBean
import com.intellij.errorreport.error.UpdateAvailableException
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.Consumer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean

internal val NOTIFY_SUCCESS_EACH_REPORT = AtomicBoolean(true) // dirty hack, reporter API does not support any optional args

/**
 * This is an internal implementation of [ErrorReportSubmitter] which is used to report exceptions in IntelliJ platform
 * and plugins developed by JetBrains to processing at JetBrains.
 *
 * **It's not supposed to be used by third-party plugins.**
 * Third-party plugins need to provide their own implementations of [ErrorReportSubmitter].
 */
@InternalIgnoreDependencyViolation
open class ITNReporter internal constructor(private val postUrl: String) : ErrorReportSubmitter() {
  @ApiStatus.Internal
  constructor() : this("https://ea-report.jetbrains.com/trackerRpc/idea/createScr")

  private val INTERVAL = 10 * 60 * 1000L  // an interval between exceptions to form a chain, ms

  @Volatile private var previousReport: Pair<Long, Int>? = null  // (timestamp, threadID) of last reported exception

  override fun getReportActionText(): String = DiagnosticBundle.message("error.report.to.jetbrains.action")

  override fun getPrivacyNoticeText(): String =
    DiagnosticBundle.message("error.dialog.notice.anonymous")

  override fun submit(
    events: Array<IdeaLoggingEvent>,
    additionalInfo: String?,
    parentComponent: Component,
    consumer: Consumer<in SubmittedReportInfo>
  ): Boolean {
    val event = events[0]
    val plugin =
      (event as? IdeaReportingEvent)?.plugin ?:
      event.throwable?.let { PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(it)) }
    val errorBean = createReportBean(event, additionalInfo, plugin)
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent))
    return submit(project, errorBean, parentComponent, consumer::consume)
  }

  @ApiStatus.Internal
  suspend fun submitAutomated(event: IdeaLoggingEvent, plugin: IdeaPluginDescriptor?): SubmittedReportInfo {
    val errorBean = createReportBean(event, comment = "Automatically reported exception", plugin)
    return service<ITNProxyCoroutineScopeHolder>().coroutineScope.async {
      try {
        val reportId = ITNProxy.sendError(errorBean, postUrl)
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

  @ApiStatus.Internal
  fun hostId(): String = ITNProxy.DEVICE_ID

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

  private fun submit(
    project: Project?,
    errorBean: ErrorBean,
    parentComponent: Component,
    callback: (SubmittedReportInfo) -> Unit,
  ): Boolean {
    service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch {
      try {
        val reportId = if (project != null) {
          withBackgroundProgress(project, DiagnosticBundle.message("title.submitting.error.report")) {
            ITNProxy.sendError(errorBean, postUrl)
          }
        }
        else {
          ITNProxy.sendError(errorBean, postUrl)
        }
        updatePreviousReport(errorBean.event, reportId)
        onSuccess(project, reportId, callback)
      }
      catch (e: Exception) {
        onError(project, e, errorBean, parentComponent, callback)
      }
    }
    return true
  }

  private fun onSuccess(project: Project?, reportId: Int, callback: (SubmittedReportInfo) -> Unit) {
    val reportUrl = ITNProxy.getBrowseUrl(reportId)
    callback(SubmittedReportInfo(reportUrl, reportId.toString(), SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))

    if (!NOTIFY_SUCCESS_EACH_REPORT.get()) return

    if (Registry.`is`("exception.analyzer.report.show.successful.notification")) {
      val content = DiagnosticBundle.message("error.report.gratitude")
      val title = DiagnosticBundle.message("error.report.submitted")
      val notification = Notification("Error Report", title, content, NotificationType.INFORMATION).setImportant(false)
      if (reportUrl != null) {
        notification.addAction(NotificationAction.createSimpleExpiring(DiagnosticBundle.message("error.report.view.action")) { BrowserUtil.browse(reportUrl) })
      }
      notification.notify(project)
    }
  }

  private suspend fun onError(
    project: Project?,
    e: Exception,
    errorBean: ErrorBean,
    parentComponent: Component,
    callback: (SubmittedReportInfo) -> Unit
  ) {
    val logger = Logger.getInstance(ITNReporter::class.java)
    logger.info("reporting failed: ${e}")
    logger.debug(e)
    withContext(Dispatchers.EDT) {
      if (e is UpdateAvailableException) {
        val message = DiagnosticBundle.message("error.report.new.build.message", e.message)
        val title = DiagnosticBundle.message("error.report.new.build.title")
        val icon = Messages.getWarningIcon()
        if (parentComponent.isShowing) Messages.showMessageDialog(parentComponent, message, title, icon)
        else Messages.showMessageDialog(project, message, title, icon)
        callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else if (e is CancellationException) {
        callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      }
      else {
        val message = DiagnosticBundle.message("error.report.failed.message", e.message)
        val title = DiagnosticBundle.message("error.report.failed.title")
        val result = MessageDialogBuilder.yesNo(title, message).ask(project)
        if (!result || !submit(project, errorBean, parentComponent, callback)) {
          callback(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
        }
      }
    }
  }
}
