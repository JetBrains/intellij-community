// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.CommonBundle
import com.intellij.diagnostic.IdeErrorsDialog.ReportAction.entries
import com.intellij.diagnostic.MessagePool.TooManyErrorsException
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.util.ExceptionUtil
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.GridBagConstraints.*
import java.awt.event.ActionEvent
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.util.*
import java.util.function.Predicate
import java.util.zip.CRC32
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.text.JTextComponent

open class IdeErrorsDialog @ApiStatus.Internal constructor(
  private val myMessagePool: MessagePool,
  private val myProject: Project?,
  defaultMessage: LogMessage?
) : DialogWrapper(myProject, true), MessagePoolListener, UiDataProvider {
  private val myAcceptedNotices: MutableSet<String>
  private val myMessageClusters: MutableList<MessageCluster> = ArrayList() // exceptions with the same stacktrace
  private var myIndex: Int
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
    title = DiagnosticBundle.message("error.list.title")
    isModal = false
    @Suppress("LeakingThis")
    init()
    setCancelButtonText(CommonBundle.message("close.action.name"))
    val rawValue = PropertiesComponent.getInstance().getValue(ACCEPTED_NOTICES_KEY, "")
    myAcceptedNotices = Collections.synchronizedSet(LinkedHashSet(rawValue.split(ACCEPTED_NOTICES_SEPARATOR)))
    updateMessages()
    myIndex = selectMessage(defaultMessage)
    updateControls()
    @Suppress("LeakingThis")
    myMessagePool.addListener(this)
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
    if (defaultMessage != null) {
      for (i in myMessageClusters.indices) {
        if (myMessageClusters[i].messages.contains(defaultMessage)) return i
      }
    }
    else {
      for (i in myMessageClusters.indices) {
        if (!myMessageClusters[i].messages[0].isRead) return i
      }
      for (i in myMessageClusters.indices) {
        for (message in myMessageClusters[i].messages) {
          if (!message.isRead) return i
        }
      }
      for (i in myMessageClusters.indices) {
        if (!myMessageClusters[i].messages[0].isSubmitted) return i
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
      ActionPlaces.TOOLBAR_DECORATOR_TOOLBAR, DefaultActionGroup(BackAction(), ForwardAction()), true)
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
    isOKActionEnabled = cluster.canSubmit() && !cluster.detailsText.isNullOrBlank() && myUpdateControlsJob.isCompleted
  }

  override fun createCenterPanel(): JComponent? {
    val editorFont = EditorUtil.getEditorFont()
    myCommentArea = JBTextArea(5, 0)
    myCommentArea.font = editorFont
    myCommentArea.emptyText.text = DiagnosticBundle.message("error.dialog.comment.prompt")
    myCommentArea.margin = JBUI.insets(2)
    myCommentArea.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        selectedMessage().additionalInfo = myCommentArea.text.trim { it <= ' ' }
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
        myAttachmentArea.text = cluster.detailsText
        myAttachmentArea.isEditable = cluster.isUnsent
      }
      else {
        myAttachmentArea.text = selectedMessage().allAttachments[index - 1].displayText
        myAttachmentArea.isEditable = false
      }
      myAttachmentArea.caretPosition = 0
    }
    myAttachmentList.setCheckBoxListListener { index: Int, value: Boolean ->
      if (index > 0) {
        selectedMessage().allAttachments[index - 1].isIncluded = value
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
          cluster.detailsText = detailsText
          enableOkButtonIfReady()
        }
      }
    })
    @NlsSafe val heightSample = " "
    myCredentialLabel = SwingHelper.createHtmlViewer(false, null, null, null).apply {
      text = heightSample
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          selectedCluster().submitter?.let { submitter ->
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
    return if (SystemInfo.isWindows) {
      arrayOf(okAction, ClearErrorsAction(), cancelAction)
    }
    else {
      arrayOf(ClearErrorsAction(), cancelAction, okAction)
    }
  }

  override fun createLeftSideActions(): Array<Action> {
    if (myProject != null && !myProject.isDefault && PluginManagerCore.isPluginInstalled(PluginId.getId(ITNProxy.EA_PLUGIN_ID))) {
      ActionManager.getInstance().getAction("Unscramble")?.let {
        return arrayOf(AnalyzeAction(it))
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

  private fun selectedCluster(): MessageCluster = myMessageClusters[myIndex]

  private fun selectedMessage(): AbstractMessage = selectedCluster().first

  private fun updateMessages() {
    val messages = myMessagePool.getFatalErrors(true, true)
    val clusters: MutableMap<Long, MessageCluster> = LinkedHashMap()
    for (message in messages) {
      val digest = CRC32()
      digest.update(ExceptionUtil.getThrowableText(message.throwable).toByteArray(StandardCharsets.UTF_8))
      clusters.computeIfAbsent(digest.value) { MessageCluster(message) }.messages.add(message)
    }
    myMessageClusters.clear()
    myMessageClusters.addAll(clusters.values)
  }

  @RequiresEdt
  private fun updateControls() {
    myLoadingDecorator.startLoading(false)
    myUpdateControlsJob.cancel(null)
    myUpdateControlsJob = service<ITNProxyCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
      val cluster = selectedCluster()
      val submitter = cluster.submitter
      cluster.messages.forEach { it.isRead = true }
      updateLabels(cluster)
      updateDetails(cluster)
      updateCredentialsPanel(submitter)
      isOKActionEnabled = cluster.canSubmit()
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

  private suspend fun updateLabels(cluster: MessageCluster) {
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
    val plugin = cluster.plugin
    val info = StringBuilder()
    if (pluginId != null) {
      val name = if (plugin != null) plugin.name else pluginId.toString()
      if (plugin != null && (!plugin.isBundled || plugin.allowBundledUpdate())) {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin.version", name, plugin.version))
      }
      else {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin", name))
      }
    }
    else if (t is AbstractMethodError) {
      info.append(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"))
    }
    else if (t is Freeze) {
      info.append(DiagnosticBundle.message("error.list.message.blame.freeze"))
    }
    else if (t is JBRCrash) {
      info.append(DiagnosticBundle.message("error.list.message.blame.jbr.crash"))
    }
    else if (t is KotlinCompilerCrash) {
      info.append(DiagnosticBundle.message("error.list.message.blame.kotlin.crash")).append(' ').append(t.version)
    }
    else {
      info.append(DiagnosticBundle.message("error.list.message.blame.core", ApplicationNamesInfo.getInstance().productName))
    }
    if (pluginId != null && !ApplicationInfo.getInstance().isEssentialPlugin(pluginId)) {
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
    if (submitter == null && plugin != null && !PluginManagerCore.isDevelopedByJetBrains(plugin)) {
      myForeignPluginWarningLabel.isVisible = true
      val vendor = plugin.vendor
      val vendorUrl =
        plugin.vendorUrl?.trim()?.let { URLUtil.addSchemaIfMissing(it) }?.takeIf(::isValidUrl)
        ?: plugin.vendorEmail?.trim()?.let { "mailto:" + it.removePrefix("mailto:") }?.takeIf(::isValidUrl)
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

  private fun isValidUrl(url: String): Boolean = runCatching { URL(url) }.isSuccess

  private fun updateDetails(cluster: MessageCluster) {
    val message = cluster.first
    val canReport = cluster.canSubmit()
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
    myCommentArea.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                                    if (canReport) null else Predicate<JBTextArea> { false })
    myAttachmentList.setEditable(canReport)
  }

  private suspend fun updateCredentialsPanel(submitter: ErrorReportSubmitter?) {
    myCredentialLabel.isVisible = false
    if (submitter != null) {
      loadCredentialsPanel(submitter)
    }
  }

  private fun reportMessage(cluster: MessageCluster, dialogClosed: Boolean): Boolean {
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

    val (userMessage, stacktrace) = cluster.decouple()
    val events = arrayOf<IdeaLoggingEvent>(IdeaReportingEvent(message, userMessage, stacktrace, cluster.plugin))
    var parentComponent: Container = rootPane
    if (dialogClosed) {
      val frame = ComponentUtil.getParentOfType(IdeFrame::class.java, parentComponent)
      parentComponent = frame?.component ?: WindowManager.getInstance().findVisibleFrame() ?: parentComponent
    }

    val accepted = submitter.submit(events, message.additionalInfo, parentComponent) { reportInfo: SubmittedReportInfo? ->
      message.setSubmitted(reportInfo)
      UIUtil.invokeLaterIfNeeded { updateOnSubmit() }
    }
    if (!accepted) {
      message.isSubmitting = false
    }
    return accepted
  }

  private fun disablePlugin() {
    val plugin = selectedCluster().plugin
    if (plugin != null) {
      confirmDisablePlugins(myProject, listOf(plugin))
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
    UIUtil.invokeLaterIfNeeded {
      if (isShowing) {
        updateMessages()
        updateControls()
      }
    }
  }

  override fun poolCleared() {
    UIUtil.invokeLaterIfNeeded {
      if (isShowing) {
        doCancelAction()
      }
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CURRENT_TRACE_KEY] = selectedMessage().throwableText
  }

  /* helpers */
  private class MessageCluster(val first: AbstractMessage) {
    val pluginId: PluginId? = PluginUtil.getInstance().findPluginId(first.throwable)
    val plugin: IdeaPluginDescriptor? = PluginManagerCore.getPlugin(pluginId)
    val submitter: ErrorReportSubmitter? = getSubmitter(first.throwable, plugin)
    var detailsText: String? = detailsText()
    val messages: MutableList<AbstractMessage> = ArrayList()

    private fun detailsText(): String? {
      val t = first.throwable
      if (t is TooManyErrorsException) {
        return t.message
      }
      val userMessage = first.message
      val stacktrace = first.throwableText
      return if (userMessage.isNullOrBlank()) stacktrace else "${userMessage}\n\n${stacktrace}"
    }

    val isUnsent: Boolean
      get() = !(first.isSubmitted || first.isSubmitting)

    fun canSubmit(): Boolean = submitter != null && isUnsent

    fun decouple(): Pair<String?, String> {
      val className = first.throwable.javaClass.name
      val detailsText = detailsText!!
      val p = detailsText.indexOf(className)
      return when {
        p == 0 -> null to detailsText
        p > 0 && detailsText[p - 1] == '\n' -> {
          detailsText.substring(0, p).trim { it <= ' ' } to detailsText.substring(p)
        }
        else -> "*** exception class was changed or removed" to detailsText
      }
    }
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

        val reportingStarted = reportMessage(selectedCluster(), closeDialog)
        if (!closeDialog) {
          updateControls()
        }
        else if (reportingStarted) {
          super@IdeErrorsDialog.doOKAction()
        }
      }
    }
  }

  private val gratitudeMessagesInternal: List<String> = listOf<String>(
    "You are breathtaking!",
    "The world is a better place because of you!",
    "I couldn’t have done this without you. Thank you for being there!",
    "Your effort and dedication don’t go unnoticed. Thank you!",
    "Thank you for making such a big difference with your actions!",
    "I feel so fortunate to have someone like you in my life!",
    "I’m just grateful to be a part of this journey with you. Stay awesome."
  )

  private fun notifySuccessReportAll() {
    val content = if (application.isInternal)
      gratitudeMessagesInternal.random()
    else
      DiagnosticBundle.message("error.report.gratitude")

    val title = DiagnosticBundle.message("error.reports.submitted")
    val notification = Notification("Error Report", title, content, NotificationType.INFORMATION).setImportant(false)
    notification.notify(myProject)
  }

  private inner class ReportAllAction : AbstractAction(DiagnosticBundle.message("error.report.all.action")) {
    override fun actionPerformed(e: ActionEvent) {
      if (isEnabled) {
        IdeErrorDialogUsageCollector.logReportAll()
        PropertiesComponent.getInstance().setValue(LAST_OK_ACTION, ReportAction.REPORT_ALL.name)
        val reportingStarted = reportAll()
        if (reportingStarted) {
          notifySuccessReportAll()
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
          notifySuccessReportAll()
          myMessagePool.clearErrors()
          super@IdeErrorsDialog.doOKAction()
        }
      }
    }
  }

  private fun reportAll(): Boolean {
    var reportingStarted = true
    for (i in myMessageClusters.indices) {
      val cluster = myMessageClusters[i]
      if (!cluster.canSubmit()) {
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

  companion object {
    private const val STACKTRACE_ATTACHMENT = "stacktrace.txt"
    private const val ACCEPTED_NOTICES_KEY = "exception.accepted.notices"
    private const val ACCEPTED_NOTICES_SEPARATOR = ":"
    private const val DISABLE_PLUGIN_URL = "#disable"
    private const val LAST_OK_ACTION = "IdeErrorsDialog.LAST_OK_ACTION"

    @JvmField val ERROR_HANDLER_EP: ExtensionPointName<ErrorReportSubmitter> = create("com.intellij.errorHandler")
    @JvmField val CURRENT_TRACE_KEY: DataKey<String> = DataKey.create("current_stack_trace_key")

    @JvmStatic
    fun confirmDisablePlugins(project: Project?, pluginsToDisable: List<IdeaPluginDescriptor>) {
      if (pluginsToDisable.isEmpty()) {
        return
      }
      val pluginIdsToDisable = pluginsToDisable.mapTo(HashSet()) { obj: IdeaPluginDescriptor -> obj.pluginId }
      val hasDependents = morePluginsAffected(pluginIdsToDisable)
      val canRestart = ApplicationManager.getApplication().isRestartCapable
      val message =
        "<html>" +
        if (pluginsToDisable.size == 1) {
          val plugin = pluginsToDisable.iterator().next()
          DiagnosticBundle.message("error.dialog.disable.prompt", plugin.name) + "<br/>" +
          DiagnosticBundle.message(if (hasDependents) "error.dialog.disable.prompt.deps" else "error.dialog.disable.prompt.lone")
        }
        else {
          DiagnosticBundle.message("error.dialog.disable.prompt.multiple") + "<br/>" +
          DiagnosticBundle.message(if (hasDependents) "error.dialog.disable.prompt.deps.multiple" else "error.dialog.disable.prompt.lone.multiple")
        } + "<br/><br/>" +
        DiagnosticBundle.message(if (canRestart) "error.dialog.disable.plugin.can.restart" else "error.dialog.disable.plugin.no.restart") +
        "</html>"
      val title = DiagnosticBundle.message("error.dialog.disable.plugin.title")
      val disable = DiagnosticBundle.message("error.dialog.disable.plugin.action.disable")
      val cancel = IdeBundle.message("button.cancel")
      val doDisable: Boolean
      val doRestart: Boolean
      if (canRestart) {
        val restart = DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart")
        val result = Messages.showYesNoCancelDialog(project, message, title, disable, restart, cancel, Messages.getQuestionIcon())
        doDisable = result == Messages.YES || result == Messages.NO
        doRestart = result == Messages.NO
      }
      else {
        val result = Messages.showYesNoDialog(project, message, title, disable, cancel, Messages.getQuestionIcon())
        doDisable = result == Messages.YES
        doRestart = false
      }
      if (doDisable) {
        PluginEnabler.HEADLESS.disable(pluginsToDisable)
        if (doRestart) {
          ApplicationManager.getApplication().restart()
        }
      }
    }

    private fun morePluginsAffected(pluginIdsToDisable: Set<PluginId>): Boolean {
      val pluginIdMap = PluginManagerCore.buildPluginIdMap()
      for (rootDescriptor in PluginManagerCore.plugins) {
        if (!rootDescriptor.isEnabled || pluginIdsToDisable.contains(rootDescriptor.pluginId)) {
          continue
        }
        if (!PluginManagerCore.processAllNonOptionalDependencies((rootDescriptor as IdeaPluginDescriptorImpl), pluginIdMap) { descriptor ->
            when {
              descriptor.isEnabled -> if (pluginIdsToDisable.contains(descriptor.pluginId)) FileVisitResult.TERMINATE
              else FileVisitResult.CONTINUE
              else -> FileVisitResult.SKIP_SUBTREE
            }
          } /* no need to process its dependencies */
        ) {
          return true
        }
      }
      return false
    }

    @JvmStatic
    fun getPlugin(event: IdeaLoggingEvent): IdeaPluginDescriptor? {
      var plugin: IdeaPluginDescriptor? = null
      if (event is IdeaReportingEvent) {
        plugin = event.plugin
      }
      else {
        val t = event.throwable
        if (t != null) {
          plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(t))
        }
      }
      return plugin
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated("use {@link PluginUtil#findPluginId} ", ReplaceWith("PluginUtil.getInstance().findPluginId(t)"))
    fun findPluginId(t: Throwable): PluginId? =
      PluginUtil.getInstance().findPluginId(t)

    @JvmStatic
    fun getSubmitter(t: Throwable, pluginId: PluginId?): ErrorReportSubmitter? =
      getSubmitter(t, PluginManagerCore.getPlugin(pluginId))

    private fun getSubmitter(t: Throwable, plugin: IdeaPluginDescriptor?): ErrorReportSubmitter? {
      if (t is TooManyErrorsException || t is AbstractMethodError) {
        return null
      }
      val reporters: List<ErrorReportSubmitter> = try {
        ERROR_HANDLER_EP.extensionList
      }
      catch (_: Throwable) {
        return null
      }
      if (plugin != null) {
        for (reporter in reporters) {
          val descriptor = reporter.pluginDescriptor
          if (descriptor != null && plugin.pluginId == descriptor.pluginId) {
            return reporter
          }
        }
      }
      if (plugin == null || PluginManagerCore.isDevelopedByJetBrains(plugin)) {
        for (reporter in reporters) {
          val descriptor = reporter.pluginDescriptor
          if (descriptor == null || PluginManagerCore.CORE_ID == descriptor.pluginId) {
            return reporter
          }
        }
      }
      return null
    }
  }
}
