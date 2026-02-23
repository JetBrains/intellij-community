// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.diagnostic.MessagePool.TooManyErrorsException
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.impl.diagnostic.errorsDialog.ErrorMessageCluster
import com.intellij.platform.ide.impl.diagnostic.errorsDialog.ErrorMessageClustering
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.util.ExceptionUtil
import com.intellij.util.application
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import com.intellij.util.system.OS
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.EAST
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagConstraints.NONE
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagConstraints.WEST
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.function.Predicate
import java.util.zip.CRC32
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.text.JTextComponent

open class IdeErrorsDialog @ApiStatus.Internal @JvmOverloads constructor(
  private val myMessagePool: MessagePool,
  private val myProject: Project?,
  private val ijProject: Boolean,
  defaultMessage: LogMessage?,
  isModal: Boolean = false,
  actionLeadToError: @Nls String? = null, // Which action led to this error (user-readable description)
  private val hideClearButton: Boolean = false,
) : DialogWrapper(myProject, true), MessagePoolListener, UiDataProvider {
  private val myAcceptedNotices: MutableSet<String>
  @Volatile
  private var myMessageClusters = emptyList<ErrorMessageCluster>() // exceptions with the same stacktrace
  @Volatile
  private var myIndex: Int = 0
    set(value) {
      field = value.coerceIn(0, (myMessageClusters.size - 1).coerceAtLeast(0))
    }
  private var myLastIndex = -1
  private var myUpdateControlsJob: Job = SupervisorJob()

  private lateinit var myCountLabel: JLabel
  private lateinit var myInfoLabel: JTextComponent
  private lateinit var myDetailsLabel: JBLabel
  private lateinit var myForeignPluginWarningLabel: JTextComponent
  private lateinit var myCommentArea: JBTextArea
  private lateinit var myAttachmentList: AttachmentList
  private lateinit var myAttachmentArea: JTextArea
  private lateinit var myPrivacyNotice: PrivacyNotice
  private lateinit var myCredentialLabel: JTextComponent
  private lateinit var myLoadingDecorator: LoadingDecorator

  init {
    title = if (actionLeadToError != null)
      DiagnosticBundle.message("error.list.title.with.action", actionLeadToError)
    else
      DiagnosticBundle.message("error.list.title")
    this.isModal = isModal
    @Suppress("LeakingThis")
    init()
    setCancelButtonText(CommonBundle.message("close.action.name"))
    val rawValue = PropertiesComponent.getInstance().getValue(ACCEPTED_NOTICES_KEY, "")
    myAcceptedNotices = Collections.synchronizedSet(LinkedHashSet(rawValue.split(ACCEPTED_NOTICES_SEPARATOR)))
    myLoadingDecorator.startLoading(false)
    updateMessages(defaultMessage)
    @Suppress("LeakingThis")
    myMessagePool.addListener(this)
    peer.isMaximizable = true
  }

  private suspend fun loadCredentialsPanel(submitter: ErrorReportSubmitter) {
    withContext(serviceAsync<ITNProxyCoroutineScopeHolder>().dispatcher) {
      val account = submitter.reporterAccount
      if (account != null) {
        withContext(Dispatchers.EDT) {
          myCredentialLabel.isVisible = true
          myCredentialLabel.text = if (account.isEmpty()) {
            DiagnosticBundle.message("error.dialog.submit.anonymous")
          }
          else {
            DiagnosticBundle.message("error.dialog.submit.named", account)
          }
        }
      }
    }
  }

  private suspend fun loadPrivacyNoticeText(submitter: ErrorReportSubmitter) {
    withContext(serviceAsync<ITNProxyCoroutineScopeHolder>().dispatcher) {
      val notice = submitter.privacyNoticeText
      if (notice != null) {
        withContext(Dispatchers.EDT) {
          myPrivacyNotice.panel.isVisible = true
          val hash = Integer.toHexString(Strings.stringHashCodeIgnoreWhitespaces(notice))
          myPrivacyNotice.expanded = !myAcceptedNotices.contains(hash)
          myPrivacyNotice.setPrivacyPolicy(notice)
        }
      }
    }
  }

  private fun selectMessage(defaultMessage: LogMessage?): Int {
    val messageClusters = myMessageClusters
    if (defaultMessage != null) {
      for (i in messageClusters.indices) {
        if (messageClusters[i].messages.contains(defaultMessage)) return i
      }
    }
    else {
      for (i in messageClusters.indices) {
        if (!messageClusters[i].messages[0].isRead) return i
      }
      for (i in messageClusters.indices) {
        for (message in messageClusters[i].messages) {
          if (!message.isRead) return i
        }
      }
      for (i in messageClusters.indices) {
        if (!messageClusters[i].messages[0].isSubmitted) return i
      }
    }
    return 0
  }

  override fun createNorthPanel(): JComponent? {
    myCountLabel = JBLabel()
    myInfoLabel = SwingHelper.createHtmlViewer(false, null, null, null).apply {
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED && DISABLE_PLUGIN_URL == it.description) {
          disablePlugin()
        }
        else {
          BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(it)
        }
      }
    }
    myDetailsLabel = JBLabel()
    myDetailsLabel.foreground = UIUtil.getContextHelpForeground()
    myForeignPluginWarningLabel = SwingHelper.createHtmlViewer(false, null, null, null).apply {
      addHyperlinkListener {
        BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(it)
      }
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLBAR_DECORATOR_TOOLBAR,
      DefaultActionGroup(BackAction(), ForwardAction(), GoToLastAction()),
      true)
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    toolbar.component.border = JBUI.Borders.empty()
    (toolbar as ActionToolbarImpl).setForceMinimumSize(true)
    toolbar.setTargetComponent(myCountLabel)
    val panel = JPanel(GridBagLayout())
    panel.add(toolbar.component, GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(3, 0), 0, 0))
    panel.add(myCountLabel, GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, WEST, HORIZONTAL, JBUI.insets(3, 10), 0, 0))
    panel.add(myInfoLabel, GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, WEST, HORIZONTAL, JBUI.insets(3, 0), 0, 0))
    panel.add(myDetailsLabel, GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, EAST, NONE, JBUI.insets(3, 0), 0, 0))
    panel.add(myForeignPluginWarningLabel, GridBagConstraints(2, 1, 3, 1, 1.0, 0.0, WEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0))
    return panel
  }

  private fun enableOkButtonIfReady() {
    val cluster = selectedCluster()
    isOKActionEnabled = cluster != null && cluster.canSubmit && !cluster.detailsText.isNullOrBlank() && myUpdateControlsJob.isCompleted
  }

  override fun createCenterPanel(): JComponent? {
    val editorFont = EditorUtil.getEditorFont()
    myCommentArea = JBTextArea(5, 0)
    myCommentArea.font = editorFont
    myCommentArea.emptyText.text = DiagnosticBundle.message("error.dialog.comment.prompt")
    myCommentArea.margin = JBUI.insets(2)
    myCommentArea.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        selectedMessage()?.additionalInfo = myCommentArea.text.trim { it <= ' ' }
      }
    })
    myAttachmentList = AttachmentList()
    myAttachmentList.addListSelectionListener {
      val index = myAttachmentList.selectedIndex
      if (index < 0) {
        myAttachmentArea.text = ""
        myAttachmentArea.isEditable = false
      }
      else if (index == 0) {
        val cluster = selectedCluster()
        myAttachmentArea.text = cluster?.detailsText ?: ""
        myAttachmentArea.isEditable = cluster?.isUnsent ?: false
      }
      else {
        myAttachmentArea.text = selectedMessage()?.allAttachments?.getOrNull(index - 1)?.displayText ?: ""
        myAttachmentArea.isEditable = false
      }
      myAttachmentArea.caretPosition = 0
    }
    myAttachmentList.setCheckBoxListListener { index: Int, value: Boolean ->
      if (index > 0) {
        selectedMessage()?.allAttachments?.getOrNull(index - 1)?.isIncluded = value
      }
    }
    myAttachmentList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    myAttachmentArea = JTextArea()
    myAttachmentArea.font = editorFont
    myAttachmentArea.margin = JBUI.insets(2)
    myAttachmentArea.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (myAttachmentList.selectedIndex == 0) {
          val detailsText = myAttachmentArea.text
          val cluster = selectedCluster()
          cluster?.detailsText = detailsText
          enableOkButtonIfReady()
        }
      }
    })
    @NlsSafe val heightSample = " "
    myCredentialLabel = SwingHelper.createHtmlViewer(false, null, null, null).apply {
      text = heightSample
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          selectedCluster()?.submitter?.let { submitter ->
            submitter.changeReporterAccount(rootPane)
            updateControls()
          }
        }
      }
    }
    myPrivacyNotice = PrivacyNotice(
      DiagnosticBundle.message("error.dialog.notice.label"),
      DiagnosticBundle.message("error.dialog.notice.label.expanded"))
    val commentPanel = JPanel(BorderLayout())
    commentPanel.border = JBUI.Borders.emptyTop(5)
    commentPanel.add(scrollPane(myCommentArea, 0, 0), BorderLayout.CENTER)
    val attachmentsPanel = JBSplitter(false, 0.2f).apply {
      firstComponent = scrollPane(myAttachmentList, 100, 350)
      secondComponent = scrollPane(myAttachmentArea, 500, 350)
    }
    attachmentsPanel.border = JBUI.Borders.emptyTop(5)
    val accountRow = JPanel(GridBagLayout())
    accountRow.border = JBUI.Borders.empty(6, 0)
    accountRow.add(myCredentialLabel, GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBInsets.emptyInsets(), 0, 0))
    val bottomRow = JPanel(BorderLayout())
    bottomRow.add(accountRow, BorderLayout.NORTH)
    bottomRow.add(myPrivacyNotice.panel, BorderLayout.CENTER)
    bottomRow.border = JBUI.Borders.emptyBottom(6)
    val rootPanel = JPanel(BorderLayout())
    rootPanel.preferredSize = JBUI.size(800, 400)
    rootPanel.minimumSize = JBUI.size(680, 400)
    rootPanel.add(commentPanel, BorderLayout.NORTH)
    rootPanel.add(attachmentsPanel, BorderLayout.CENTER)
    rootPanel.add(bottomRow, BorderLayout.SOUTH)

    myLoadingDecorator = LoadingDecorator(rootPanel, disposable, 100, useMinimumSize = true)
    return myLoadingDecorator.component
  }

  private fun scrollPane(component: JComponent, width: Int, height: Int): JScrollPane =
    JBScrollPane(component).apply {
      if (width > 0 && height > 0) {
        this.minimumSize = JBUI.size(width, height)
      }
    }

  override fun createActions(): Array<Action> {
    val lastActionName = PropertiesComponent.getInstance().getValue(LAST_OK_ACTION)
    val lastAction = ReportAction.findOrDefault(lastActionName)
    val additionalActions = ReportAction.entries.asSequence()
      .filter { it != lastAction }
      .map { action: ReportAction -> action.getAction(this) }
      .toList()
    myOKAction = CompositeAction(lastAction.getAction(this), additionalActions)
    val clearErrorsAction = if (!hideClearButton) ClearErrorsAction() else null
    return if (OS.CURRENT == OS.Windows) {
      listOfNotNull(okAction, clearErrorsAction, cancelAction)
    }
    else {
      listOfNotNull(clearErrorsAction, cancelAction, okAction)
    }.toTypedArray()
  }

  override fun createLeftSideActions(): Array<Action> {
    if (ijProject) {
      val action = ActionManager.getInstance().getAction("Unscramble")
      if (action != null) {
        return arrayOf(AnalyzeAction(action))
      }
    }
    return emptyArray()
  }

  override fun getDimensionServiceKey(): String? = "IDE.errors.dialog"

  override fun dispose() {
    myMessagePool.removeListener(this)
    myUpdateControlsJob.cancel()
    super.dispose()
  }

  private fun selectedCluster(): ErrorMessageCluster? = myMessageClusters.getOrNull(myIndex)

  private fun selectedMessage(): AbstractMessage? = selectedCluster()?.first

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun updateMessages(defaultMessage: LogMessage?) {
    val deferred = ErrorMessageClustering.getInstance().clusterMessages()
    deferred.cancelOnDispose(disposable)
    deferred.invokeOnCompletion { error ->
      if (error != null) {
        logger<IdeErrorsDialog>().error("Error clustering messages", error)
        return@invokeOnCompletion
      }

      myMessageClusters = deferred.getCompleted()
      myIndex = selectMessage(defaultMessage)
      UIUtil.invokeLaterIfNeeded {
        if (isShowing) {
          updateControls()
        }
      }
    }
  }

  @RequiresEdt
  private fun updateControls() {
    myLoadingDecorator.startLoading(false)
    myUpdateControlsJob.cancel(null)
    var context = Dispatchers.EDT
    if (isModal) {
      context += ModalityState.any().asContextElement()
    }
    myUpdateControlsJob = service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch(context) {
      val cluster = selectedCluster()
      val submitter = cluster?.submitter
      // if there are no messages left, the dialog will be closed automatically, so there is no need to update controls in that case
      if (cluster != null) {
        cluster.messages.forEach { it.isRead = true }
        updateLabels(cluster)
        updateDetails(cluster)
      }
      updateCredentialsPanel(submitter)
      isOKActionEnabled = cluster != null && cluster.canSubmit
      setDefaultReportActionText(submitter?.reportActionText ?: DiagnosticBundle.message("error.report.impossible.action"))
      setDefaultReportActionTooltip(if (submitter != null) null else DiagnosticBundle.message("error.report.impossible.tooltip"))
      myLoadingDecorator.stopLoading()
    }
    myUpdateControlsJob.invokeOnCompletion {
      enableOkButtonIfReady()
    }
  }

  private fun setDefaultReportActionText(text: @NlsContexts.Button String) {
    val action = okAction
    if (action is CompositeAction) {
      action.setDefaultReportActionText(text)
    }
    else {
      setOKButtonText(text)
    }
  }

  private fun setDefaultReportActionTooltip(text: @NlsContexts.Tooltip String?) {
    val action = okAction
    if (action is CompositeAction) {
      action.setDefaultReportActionTooltip(text)
    }
    else {
      setOKButtonTooltip(text)
    }
  }

  private suspend fun updateLabels(cluster: ErrorMessageCluster) {
    val message = cluster.first
    myCountLabel.text = DiagnosticBundle.message("error.list.message.index.count", myIndex + 1, myMessageClusters.size)
    val t = message.throwable
    if (t is TooManyErrorsException) {
      myInfoLabel.text = t.message
      myDetailsLabel.isVisible = false
      myForeignPluginWarningLabel.isVisible = false
      myPrivacyNotice.panel.isVisible = false
    }
    val pluginId = cluster.pluginId
    val pluginInfo = cluster.pluginInfo
    val info = StringBuilder()

    if (t is RemoteSerializedThrowable && t.headerPrefix != null) {
      info.append("[${t.headerPrefix}] ")
    }

    if (pluginId != null) {
      val name = if (pluginInfo != null) pluginInfo.name else pluginId.toString()
      if (pluginInfo != null && (!pluginInfo.isBundled || pluginInfo.allowsBundledUpdate)) {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin.version", name, pluginInfo.version))
      }
      else {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin", name))
      }
    }
    else if (t.isInstance<AbstractMethodError>()) {
      info.append(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"))
    }
    else if (t.isInstance<Freeze>()) {
      info.append(DiagnosticBundle.message("error.list.message.blame.freeze"))
    }
    else if (t.isInstance<JBRCrash>()) {
      info.append(DiagnosticBundle.message("error.list.message.blame.jbr.crash"))
    }
    else if (t.isInstance<KotlinCompilerCrash>()) {
      info.append(DiagnosticBundle.message("error.list.message.blame.kotlin.crash")).append(' ').append(t.kotlinVersionOrEmpty)
    }
    else {
      info.append(DiagnosticBundle.message("error.list.message.blame.core", ApplicationNamesInfo.getInstance().productName))
    }

    if (pluginId != null
        && !ApplicationInfo.getInstance().isEssentialPlugin(pluginId)
        && !isImplementationDetailPlugin(pluginId)) {
      info.append(' ')
        .append("<a style=\"white-space: nowrap;\" href=\"$DISABLE_PLUGIN_URL\">")
        .append(DiagnosticBundle.message("error.list.disable.plugin"))
        .append("</a>")
    }

    if (message.isSubmitting) {
      info.append(' ').append(DiagnosticBundle.message("error.list.message.submitting"))
    }
    else if (message.submissionInfo != null) {
      info.append(' ').append("<span style=\"white-space: nowrap;\">")
      if (message.submissionInfo.status == SubmittedReportInfo.SubmissionStatus.FAILED) {
        val details = message.submissionInfo.linkText
        if (details != null) {
          info.append(DiagnosticBundle.message("error.list.message.submission.failed.details", details))
        }
        else {
          info.append(DiagnosticBundle.message("error.list.message.submission.failed"))
        }
      }
      else if (message.submissionInfo.url != null && message.submissionInfo.linkText != null) {
        info.append(DiagnosticBundle.message("error.list.message.submitted.as.link", message.submissionInfo.url, message.submissionInfo.linkText))
      }
      else {
        info.append(DiagnosticBundle.message("error.list.message.submitted"))
      }
      info.append("</span>")
    }

    myInfoLabel.text = info.toString()
    val count = cluster.messages.size
    val date = DateFormatUtil.formatPrettyDateTime(cluster.messages[count - 1].date)
    myDetailsLabel.text = DiagnosticBundle.message("error.list.message.info", date, count)
    val submitter = cluster.submitter
    if (submitter == null && pluginInfo != null && !PluginManagerCore.isDevelopedByJetBrains(pluginInfo.pluginId, pluginInfo.vendor, pluginInfo.organization)) {
      myForeignPluginWarningLabel.isVisible = true
      val vendor = pluginInfo.vendor
      val vendorUrl =
        pluginInfo.vendorUrl?.trim()?.let { URLUtil.addSchemaIfMissing(it) }?.takeIf(::isValidUrl)
        ?: pluginInfo.vendorEmail?.trim()?.let { "mailto:" + it.removePrefix("mailto:") }?.takeIf(::isValidUrl)
      myForeignPluginWarningLabel.text = when {
        !vendor.isNullOrEmpty() && vendorUrl != null -> DiagnosticBundle.message("error.dialog.foreign.plugin.warning", vendor, vendorUrl)
        vendorUrl != null -> DiagnosticBundle.message("error.dialog.foreign.plugin.warning.unnamed", vendorUrl)
        else -> DiagnosticBundle.message("error.dialog.foreign.plugin.warning.unknown")
      }
    }
    else {
      myForeignPluginWarningLabel.isVisible = false
    }
    myPrivacyNotice.panel.isVisible = false
    if (submitter != null) {
      loadPrivacyNoticeText(submitter)
    }
  }

  private fun isImplementationDetailPlugin(pluginId: PluginId): Boolean {
    return PluginManagerCore.getPlugin(pluginId)?.isImplementationDetail ?: false
  }

  private fun isValidUrl(url: String): Boolean = runCatching { URI(url).toURL() }.isSuccess

  private fun updateDetails(cluster: ErrorMessageCluster) {
    val message = cluster.first
    val canReport = cluster.canSubmit
    if (myLastIndex != myIndex) {
      myCommentArea.text = message.additionalInfo
      myAttachmentList.clear()
      myAttachmentList.addItem(STACKTRACE_ATTACHMENT, true)
      for (attachment in message.allAttachments) {
        myAttachmentList.addItem(attachment.name, attachment.isIncluded)
      }
      myAttachmentList.selectedIndex = 0
      myLastIndex = myIndex
    }
    myCommentArea.isEditable = canReport
    myCommentArea.putClientProperty(
      TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
      if (canReport) null else Predicate<JBTextArea> { false }
    )
    myAttachmentList.setEditable(canReport)
  }

  private suspend fun updateCredentialsPanel(submitter: ErrorReportSubmitter?) {
    myCredentialLabel.isVisible = false
    if (submitter != null) {
      loadCredentialsPanel(submitter)
    }
  }

  private fun reportMessage(cluster: ErrorMessageCluster, dialogClosed: Boolean): Boolean {
    val submitter = cluster.submitter ?: return false
    val message = cluster.first
    message.isSubmitting = true

    service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch {
      val notice = submitter.privacyNoticeText
      if (notice != null) {
        val hash = Integer.toHexString(Strings.stringHashCodeIgnoreWhitespaces(notice))
        if (myAcceptedNotices.add(hash)) {
          PropertiesComponent.getInstance().setValue(ACCEPTED_NOTICES_KEY, myAcceptedNotices.joinToString(ACCEPTED_NOTICES_SEPARATOR))
        }
      }
    }

    val pair = cluster.decouple()
    if (pair == null) {
      message.setSubmitted(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
      return false
    }

    val events = arrayOf(IdeaLoggingEvent(pair.first, pair.second, message.includedAttachments, cluster.pluginInfo, message))
    var parentComponent: Container = rootPane
    if (dialogClosed) {
      val frame = ComponentUtil.getParentOfType(IdeFrame::class.java, parentComponent)
      parentComponent = frame?.component ?: WindowManager.getInstance().findVisibleFrame() ?: parentComponent
    }
    val accepted = submitter.submit(events, message.additionalInfo, parentComponent) { reportInfo ->
      message.setSubmitted(reportInfo)
      UIUtil.invokeLaterIfNeeded { updateOnSubmit() }
    }
    if (!accepted) {
      message.isSubmitting = false
    }
    return accepted
  }

  private fun disablePlugin() {
    selectedCluster()?.pluginInfo?.let { plugin ->
      DisablePluginsDialog.confirmDisablePlugins(myProject, listOf(plugin))
    }
  }

  protected open fun updateOnSubmit() {
    if (isShowing) {
      updateControls()
    }
  }

  /* UI components */
  private inner class BackAction : AnAction(IdeBundle.message("button.previous"), null, AllIcons.Actions.Back), DumbAware, LightEditCompatible {
    init {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB)
      if (action != null) {
        registerCustomShortcutSet(action.shortcutSet, rootPane, disposable)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myIndex > 0
    }

    override fun actionPerformed(e: AnActionEvent) {
      myLastIndex = myIndex--
      updateControls()
    }
  }

  private inner class ForwardAction : AnAction(IdeBundle.message("button.next"), null, AllIcons.Actions.Forward), DumbAware, LightEditCompatible {
    init {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB)
      if (action != null) {
        registerCustomShortcutSet(action.shortcutSet, rootPane, disposable)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myIndex < myMessageClusters.size - 1
    }

    override fun actionPerformed(e: AnActionEvent) {
      myLastIndex = myIndex++
      updateControls()
    }
  }

  private inner class GoToLastAction : AnAction(IdeBundle.message("button.last"), null, AllIcons.Actions.Play_last), DumbAware, LightEditCompatible {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = myIndex != myMessageClusters.size - 1
    }

    override fun actionPerformed(e: AnActionEvent) {
      myLastIndex = myIndex
      myIndex = myMessageClusters.size - 1
      updateControls()
    }
  }

  private inner class ClearErrorsAction : AbstractAction(DiagnosticBundle.message("error.dialog.clear.all.action")) {
    override fun actionPerformed(e: ActionEvent) {
      IdeErrorDialogUsageCollector.logClearAll()
      myMessagePool.clearErrors()
      doCancelAction()
    }
  }

  private inner class AnalyzeAction(analyze: AnAction) : AbstractAction(ActionsBundle.actionText(ActionManager.getInstance().getId(analyze))) {
    private val myAnalyze: AnAction

    init {
      putValue(MNEMONIC_KEY, analyze.templatePresentation.mnemonic)
      myAnalyze = analyze
    }

    override fun actionPerformed(e: ActionEvent) {
      val ctx = DataManager.getInstance().getDataContext(e.source as Component)
      val event = AnActionEvent.createEvent(myAnalyze, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
      myAnalyze.actionPerformed(event)
      doCancelAction()
    }
  }

  private class AttachmentList : CheckBoxList<String>() {
    private var myEditable = true

    fun addItem(item: @NlsContexts.Checkbox String, selected: Boolean) {
      addItem(item, "${item}  ", selected)
    }

    fun setEditable(editable: Boolean) {
      myEditable = editable
    }

    override fun isEnabled(index: Int): Boolean = myEditable && index > 0
  }

  /* interfaces */
  override fun newEntryAdded() {
    updateMessages(defaultMessage = null)
  }

  override fun poolCleared() {
    UIUtil.invokeLaterIfNeeded {
      if (isShowing) {
        doCancelAction()
      }
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CURRENT_TRACE_KEY] = selectedMessage()?.throwableText
  }

  private class CompositeAction(mainAction: Action, additionalActions: List<Action>) :
    AbstractAction(mainAction.getValue(NAME) as String?), OptionAction {

    private val myMainAction: Action
    private val myAdditionalActions: List<Action>

    init {
      putValue(DEFAULT_ACTION, true)
      myMainAction = mainAction
      myAdditionalActions = additionalActions
    }

    override fun actionPerformed(e: ActionEvent) {
      myMainAction.actionPerformed(e)
    }

    override fun setEnabled(isEnabled: Boolean) {
      super.setEnabled(isEnabled)
      myMainAction.isEnabled = isEnabled
      for (additionalAction in myAdditionalActions) {
        additionalAction.isEnabled = isEnabled
      }
    }

    override fun getOptions(): Array<Action> = myAdditionalActions.toTypedArray<Action>()

    fun setDefaultReportActionText(text: @NlsContexts.Button String) {
      putDefaultReportActionValue(NAME, text)
    }

    fun setDefaultReportActionTooltip(text: @NlsContexts.Tooltip String?) {
      putDefaultReportActionValue(SHORT_DESCRIPTION, text)
    }

    private fun putDefaultReportActionValue(key: String, value: Any?) {
      if (myMainAction is DefaultReportAction) {
        putValue(key, value)
        myMainAction.putValue(key, value)
      }
      else {
        for (action in myAdditionalActions) {
          (action as? DefaultReportAction)?.putValue(key, value)
        }
      }
    }
  }

  private inner class DefaultReportAction : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      if (isEnabled) {
        IdeErrorDialogUsageCollector.logReport()
        PropertiesComponent.getInstance().setValue(LAST_OK_ACTION, ReportAction.DEFAULT.name)
        val closeDialog = myMessageClusters.size == 1

        NOTIFY_SUCCESS_EACH_REPORT.set(true)

        val selectedCluster = selectedCluster()
        val reportingStarted = selectedCluster != null && reportMessage(selectedCluster, closeDialog)
        if (!reportingStarted) {
          if (!closeDialog) {
            updateControls()
          }
          return
        }

        updateControls()
        val autoReportEnabled = suggestEnablingAutoReportIfApplicable()
        if (!autoReportEnabled) {
          if (closeDialog) {
            super@IdeErrorsDialog.doOKAction()
          }
          return
        }
        val reportAllStarted = reportAll(true)
        if (reportAllStarted) {
          notifySuccessReportAll(true)
          super@IdeErrorsDialog.doOKAction()
        }
        else {
          updateControls()
        }
      }
    }
  }

  private val gratitudeMessagesInternal: List<String> = listOf(
    "You are breathtaking!",
    "The world is a better place because of you!",
    "I couldn’t have done this without you. Thank you for being there!",
    "Your effort and dedication don’t go unnoticed. Thank you!",
    "Thank you for making such a big difference with your actions!",
    "I feel so fortunate to have someone like you in my life!",
    "I’m just grateful to be a part of this journey with you. Stay awesome.",
    "Your presence alone makes a difference—never forget that!",
    "You’re a gift to those around you. Stay incredible!",
    "I truly appreciate everything you do—you're amazing!",
    "You make even the toughest days feel lighter. Thank you!",
    "The world needs more people like you!",
    "Thank you for chipping in. Together, we can achieve anything.",
  )

  private fun notifySuccessReportAll(withAutoReportEnabled: Boolean) {
    val gratitude = if (application.isInternal) gratitudeMessagesInternal.random() else DiagnosticBundle.message("error.report.gratitude")
    val content = if (withAutoReportEnabled) DiagnosticBundle.message("error.report.with.auto.report.enabled", gratitude) else gratitude
    val title = DiagnosticBundle.message("error.reports.submitted")
    val notification = Notification("Error Report", title, content, NotificationType.INFORMATION).setImportant(false)
    notification.notify(myProject)
  }

  /**
   *  Returns true if a user enabled an automatic error report on this request
   */
  private fun suggestEnablingAutoReportIfApplicable(): Boolean {
    if (!ExceptionAutoReportUtil.shouldOfferEnablingAutoReport()) {
      return false
    }
    val dialogResult = MessageDialogBuilder.yesNo(
      DiagnosticBundle.message("auto.report.suggestion.dialog.title"),
      DiagnosticBundle.message("auto.report.suggestion.dialog.message"),
    )
      .yesText(DiagnosticBundle.message("auto.report.suggestion.dialog.yes.option"))
      .noText(DiagnosticBundle.message("auto.report.suggestion.dialog.no.option"))
      .ask(rootPane)

    ExceptionAutoReportUtil.enablingAutoReportOffered(dialogResult)
    return dialogResult
  }

  private inner class ReportAllAction : AbstractAction(DiagnosticBundle.message("error.report.all.action")) {
    override fun actionPerformed(e: ActionEvent) {
      if (isEnabled) {
        IdeErrorDialogUsageCollector.logReportAll()
        PropertiesComponent.getInstance().setValue(LAST_OK_ACTION, ReportAction.REPORT_ALL.name)
        val reportingStarted = reportAll()
        if (reportingStarted) {
          val autoReportEnabled = suggestEnablingAutoReportIfApplicable()
          notifySuccessReportAll(autoReportEnabled)
          super@IdeErrorsDialog.doOKAction()
        }
      }
    }
  }

  private inner class ReportAndClearAllAction : AbstractAction(DiagnosticBundle.message("error.report.and.clear.all.action")) {
    override fun actionPerformed(e: ActionEvent) {
      if (isEnabled) {
        IdeErrorDialogUsageCollector.logReportAndClearAll()
        PropertiesComponent.getInstance().setValue(LAST_OK_ACTION, ReportAction.REPORT_AND_CLEAR_ALL.name)
        val reportingStarted = reportAll()
        if (reportingStarted) {
          myMessagePool.clearErrors()
          val autoReportEnabled = suggestEnablingAutoReportIfApplicable()
          notifySuccessReportAll(autoReportEnabled)
          super@IdeErrorsDialog.doOKAction()
        }
      }
    }
  }

  private fun reportAll(onlyEligibleForAutoReport: Boolean = false): Boolean {
    var reportingStarted = true
    val messageClusters = myMessageClusters
    for (i in messageClusters.indices) {
      val cluster = messageClusters[i]
      if (!cluster.canSubmit) {
        continue
      }

      if (onlyEligibleForAutoReport && !ExceptionAutoReportUtil.isAutoReportableException(cluster.first)) {
        continue
      }

      NOTIFY_SUCCESS_EACH_REPORT.set(false)

      if (!reportMessage(cluster, true).also { reportingStarted = it }) {
        myIndex = i
        updateControls()
        break
      }
    }
    return reportingStarted
  }

  private enum class ReportAction(private val myActionProducer: (IdeErrorsDialog) -> Action) {
    DEFAULT({ dialog: IdeErrorsDialog -> dialog.DefaultReportAction() }),
    REPORT_ALL({ dialog: IdeErrorsDialog -> dialog.ReportAllAction() }),
    REPORT_AND_CLEAR_ALL({ dialog: IdeErrorsDialog -> dialog.ReportAndClearAllAction() });

    fun getAction(dialog: IdeErrorsDialog): Action = myActionProducer(dialog)

    companion object {
      fun findOrDefault(name: String?): ReportAction {
        if (name != null) {
          for (value in entries) {
            if (value.name == name) {
              return value
            }
          }
        }
        return defaultAction
      }

      private val defaultAction: ReportAction
        get() = if (ApplicationManager.getApplication().isInternal) DEFAULT else REPORT_AND_CLEAR_ALL
    }
  }

  private inline fun <reified T : Throwable> Throwable.isBackendInstance() =
    this is RemoteSerializedThrowable && classFqn == T::class.qualifiedName

  private inline fun <reified T : Throwable> Throwable.isInstance() = this is T || isBackendInstance<T>()

  // This is a very hacky method, since no actual cast is done for `RemoteSerializedThrowable``
  private val Throwable.kotlinVersionOrEmpty: String
    get() = (this as? KotlinCompilerCrash)?.version.orEmpty()

  @ApiStatus.Internal
  companion object {
    private const val STACKTRACE_ATTACHMENT = "stacktrace.txt"
    private const val ACCEPTED_NOTICES_KEY = "exception.accepted.notices"
    private const val ACCEPTED_NOTICES_SEPARATOR = ":"
    private const val DISABLE_PLUGIN_URL = "#disable"
    private const val LAST_OK_ACTION = "IdeErrorsDialog.LAST_OK_ACTION"

    @JvmField
    @ApiStatus.Internal
    val CURRENT_TRACE_KEY: DataKey<String> = DataKey.create("current_stack_trace_key")

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @ApiStatus.Internal
    @Deprecated("internal implementation detail; a plugin code should use `ErrorReportSubmitter.getPluginDescriptor`", level = DeprecationLevel.ERROR)
    fun getPlugin(event: IdeaLoggingEvent): IdeaPluginDescriptor? =
      event.throwable?.let { PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(it)) }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @ApiStatus.Internal
    @Deprecated("use {@link PluginUtil#findPluginId} ", ReplaceWith("PluginUtil.getInstance().findPluginId(t)"), level = DeprecationLevel.ERROR)
    fun findPluginId(t: Throwable): PluginId? =
      PluginUtil.getInstance().findPluginId(t)

    fun hashMessage(message: AbstractMessage): Long {
      val digest = CRC32()
      digest.update(ExceptionUtil.getThrowableText(message.throwable).toByteArray(StandardCharsets.UTF_8))
      return digest.value
    }
  }
}
