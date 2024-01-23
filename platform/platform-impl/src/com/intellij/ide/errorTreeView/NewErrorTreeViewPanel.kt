// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.actions.CloseTabToolbarAction
import com.intellij.ide.actions.ExportToTextFileToolbarAction
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration
import com.intellij.ide.errorTreeView.impl.ErrorViewTextExporter
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.content.MessageView
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.ErrorTreeView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MutableErrorTreeView
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
open class NewErrorTreeViewPanel @JvmOverloads constructor(
  @JvmField protected var project: Project,
  private val helpId: String?,
  @Suppress("UNUSED_PARAMETER") createExitAction: Boolean = true,
  createToolbar: Boolean = true,
  rerunAction: Runnable? = null,
  private val state: MessageViewState = MessageViewState(),
  errorViewStructure: ErrorViewStructure? = null,
) : JPanel(), DataProvider, OccurenceNavigator, MutableErrorTreeView, CopyProvider, Disposable {
  @ApiStatus.Internal
  @ApiStatus.Experimental
  class MessageViewState {
    @Volatile
    @JvmField
    var progressText: @NlsContexts.ProgressText String? = null

    @JvmField
    @Volatile
    var fraction: Float = 0f

    fun clearProgress() {
      progressText = null
      fraction = 0.0f
    }
  }

  val errorViewStructure: ErrorViewStructure

  private val structureModel: StructureTreeModel<ErrorViewStructure>
  private val progressFlow = MutableStateFlow<@NlsContexts.ProgressText String?>(null)

  @Volatile
  private var isDisposed = false
  private val configuration = ErrorTreeViewConfiguration.getInstance(project)

  private var leftToolbar: ActionToolbar? = null

  private val treeExpander = object : TreeExpander {
    override fun expandAll() {
      this@NewErrorTreeViewPanel.expandAll()
    }

    override fun canExpand() = true

    override fun collapseAll() {
      this@NewErrorTreeViewPanel.collapseAll()
    }

    override fun canCollapse() = true
  }

  private val exporterToTextFile: ExporterToTextFile
  @JvmField
  protected var myTree: Tree
  private val messagePanel: JPanel
  private var processController: ProcessController? = null
  private var progressLabel: JLabel? = null
  private var progressPanel: JPanel? = null
  private val autoScrollToSourceHandler: AutoScrollToSourceHandler
  private val occurrenceNavigatorSupport: MyOccurrenceNavigatorSupport

  interface ProcessController {
    fun stopProcess()

    val isProcessStopped: Boolean
  }

  private val scope = (project as ComponentManagerEx).getCoroutineScope().childScope()

  init {
    layout = BorderLayout()
    autoScrollToSourceHandler = object : AutoScrollToSourceHandler() {
      override fun isAutoScrollMode() = configuration.isAutoscrollToSource

      override fun setAutoScrollMode(state: Boolean) {
        configuration.isAutoscrollToSource = state
      }
    }
    messagePanel = JPanel(BorderLayout())
    @Suppress("LeakingThis")
    this.errorViewStructure = errorViewStructure ?: createErrorViewStructure(project = project, canHideWarnings = canHideWarnings())
    @Suppress("LeakingThis")
    structureModel = StructureTreeModel(this.errorViewStructure, this)
    @Suppress("LeakingThis")
    myTree = Tree(AsyncTreeModel(structureModel, this))
    myTree.rowHeight = 0
    @Suppress("SpellCheckingInspection")
    myTree.emptyText.text = IdeBundle.message("errortree.noMessages")
    exporterToTextFile = ErrorViewTextExporter(errorViewStructure)
    occurrenceNavigatorSupport = MyOccurrenceNavigatorSupport(myTree)
    autoScrollToSourceHandler.install(myTree)
    TreeUtil.installActions(myTree)
    myTree.isRootVisible = false
    myTree.showsRootHandles = true
    myTree.isLargeModel = true
    val scrollPane = NewErrorTreeRenderer.install(myTree)
    scrollPane.border = IdeBorderFactory.createBorder(SideBorder.LEFT)
    messagePanel.add(scrollPane, BorderLayout.CENTER)
    if (createToolbar) {
      @Suppress("LeakingThis")
      add(createToolbarPanel(rerunAction), BorderLayout.WEST)
    }
    @Suppress("LeakingThis")
    add(messagePanel, BorderLayout.CENTER)
    myTree.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        popupInvoked(comp, x, y)
      }
    })
    EditSourceOnDoubleClickHandler.install(myTree)
    EditSourceOnEnterKeyHandler.install(myTree)

    scope.launch {
      progressFlow
        .debounce(100.milliseconds)
        .collectLatest { text ->
          withContext(Dispatchers.EDT) {
            initProgressPanel()
            if (text == null) {
              progressLabel!!.text = ""
            }
            else {
              val fraction = state.fraction
              progressLabel!!.text = if (fraction > 0.0f) "${(fraction * 100 + 0.5).toInt()}%  $text" else text
            }
          }
        }
    }
  }

  companion object {
    @JvmField
    protected val LOG: Logger = logger<NewErrorTreeViewPanel>()

    @Suppress("SpellCheckingInspection")
    @JvmStatic
    fun createExportPrefix(line: Int): String = if (line < 0) "" else IdeBundle.message("errortree.prefix.line", line)

    @JvmStatic
    fun createRendererPrefix(line: Int, column: Int): String {
      if (line < 0) {
        return ""
      }
      return if (column < 0) "($line)" else "($line, $column)"
    }

    @JvmStatic
    fun getQualifiedName(file: VirtualFile): String = file.presentableUrl
  }

  protected open fun createErrorViewStructure(project: Project?, canHideWarnings: Boolean): ErrorViewStructure {
    return ErrorViewStructure(project, canHideWarnings)
  }

  override fun dispose() {
    try {
      scope.cancel()
    }
    finally {
      isDisposed = true
      errorViewStructure.clear()
    }
  }

  override fun performCopy(dataContext: DataContext) {
    val descriptors = selectedNodeDescriptors
    if (!descriptors.isEmpty()) {
      CopyPasteManager.getInstance().setContents(StringSelection(descriptors.joinToString(separator = "\n") {
        val element = it.element
        NewErrorTreeRenderer.calcPrefix(element) + element.text.joinToString(separator = "\n")
      }))
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isCopyEnabled(dataContext: DataContext): Boolean = !selectedNodeDescriptors.isEmpty()

  override fun isCopyVisible(dataContext: DataContext): Boolean = true

  val emptyText: StatusText
    get() = myTree.emptyText

  override fun getData(dataId: String): Any? {
    return when {
      PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
      CommonDataKeys.NAVIGATABLE.`is`(dataId) -> {
        val selectedMessageElement = selectedNavigatableElement
        selectedMessageElement?.navigatable
      }
      PlatformCoreDataKeys.HELP_ID.`is`(dataId) -> helpId
      PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> treeExpander
      PlatformDataKeys.EXPORTER_TO_TEXT_FILE.`is`(dataId) -> exporterToTextFile
      ErrorTreeView.CURRENT_EXCEPTION_DATA_KEY.`is`(dataId) -> {
        val selectedMessageElement = selectedErrorTreeElement
        selectedMessageElement?.data
      }
      else -> null
    }
  }

  open fun selectFirstMessage() {
    val firstError = errorViewStructure.getFirstMessage(ErrorTreeElementKind.ERROR)
    if (firstError != null) {
      selectElement(firstError) {
        if (shouldShowFirstErrorInEditor()) {
          ApplicationManager.getApplication().invokeLater(::navigateToSource, project.disposed)
        }
      }
    }
    else {
      val firstWarning = errorViewStructure.getFirstMessage(ErrorTreeElementKind.WARNING)
                         ?: errorViewStructure.getFirstMessage(ErrorTreeElementKind.NOTE)
      if (firstWarning == null) {
        TreeUtil.promiseSelectFirst(myTree)
      }
      else {
        selectElement(firstWarning, null)
      }
    }
  }

  private fun selectElement(element: ErrorTreeElement, onDone: Runnable?) {
    structureModel.select(element, myTree, Consumer { onDone?.run() })
  }

  protected open fun shouldShowFirstErrorInEditor(): Boolean = false

  open fun updateTree() {
    if (!isDisposed) {
      structureModel.invalidateAsync()
    }
  }

  override fun addMessage(type: Int, text: Array<String>, file: VirtualFile?, line: Int, column: Int, data: Any?) {
    addMessage(type = type, text = text, underFileGroup = null, file = file, line = line, column = column, data = data)
  }

  override fun addMessage(type: Int,
                          text: Array<String>,
                          underFileGroup: VirtualFile?,
                          file: VirtualFile?,
                          line: Int,
                          column: Int,
                          data: Any?) {
    if (isDisposed) {
      return
    }
    updateAddedElement(errorViewStructure.addMessage(
      ErrorTreeElementKind.convertMessageFromCompilerErrorType(type), text, underFileGroup, file, line, column, data
    ))
  }

  fun updateAddedElement(element: ErrorTreeElement) {
    var future: CompletableFuture<*>?
    val parent = errorViewStructure.getParentElement(element)
    if (parent == null) {
      future = structureModel.invalidateAsync()
    }
    else {
      future = if (parent is GroupingElement) {
        val parent2 = errorViewStructure.getParentElement(parent)
        // first, need to invalidate GroupingElement itself as it may have been just added
        if (parent2 == null) null else structureModel.invalidateAsync(parent2, true)
      }
      else {
        null
      }

      if (future == null) {
        future = structureModel.invalidateAsync(parent, true)
      }
      else {
        future = future
          // invalidateAsync for parent in any case
          .handle { _, _ -> null }
          .thenCompose { structureModel.invalidateAsync(parent, true) }
      }
    }
    if (element.kind == ErrorTreeElementKind.ERROR) {
      // expand automatically only errors
      future!!.thenRun { makeVisible(element) }
    }
  }

  protected fun makeVisible(element: ErrorTreeElement) {
    structureModel.makeVisible(element, myTree) { }
  }

  override fun addMessage(type: Int,
                          text: Array<String>,
                          groupName: String?,
                          navigatable: Navigatable,
                          exportTextPrefix: String?,
                          rendererTextPrefix: String?,
                          data: Any?) {
    if (isDisposed) {
      return
    }

    var file = if (data is VirtualFile) data else null
    if (file == null && navigatable is OpenFileDescriptor) {
      file = navigatable.file
    }
    val exportPrefix = exportTextPrefix ?: ""
    val renderPrefix = rendererTextPrefix ?: ""
    val kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type)
    updateAddedElement(errorViewStructure.addNavigatableMessage(
      groupName, navigatable, kind, text, data, exportPrefix, renderPrefix, file
    ))
  }

  fun removeMessage(type: Int, groupName: String, navigatable: Navigatable): Boolean {
    val kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type)
    val removed = errorViewStructure.removeNavigatableMessage(groupName, kind, navigatable)
    if (removed.isEmpty()) {
      return false
    }
    for (descriptor in removed) {
      updateAddedElement(descriptor)
    }
    return true
  }

  fun removeAllInGroup(name: String) {
    for (it in errorViewStructure.removeAllNavigatableMessagesInGroup(name)) {
      updateAddedElement(it)
    }
  }

  override fun getComponent(): JComponent = this

  private val selectedNavigatableElement: NavigatableErrorTreeElement?
    get() = selectedErrorTreeElement as? NavigatableErrorTreeElement

  val selectedErrorTreeElement: ErrorTreeElement?
    get() = selectedNodeDescriptor?.element

  private val selectedNodeDescriptor: ErrorTreeNodeDescriptor?
    get() = selectedNodeDescriptors.singleOrNull()

  val selectedFile: VirtualFile?
    get() {
      val descriptor = selectedNodeDescriptor
      var element = descriptor?.element
      if (element != null && element !is GroupingElement) {
        val parent = descriptor!!.parentDescriptor
        if (parent is ErrorTreeNodeDescriptor) {
          element = parent.element
        }
      }
      return if (element is GroupingElement) element.file else null
    }

  private val selectedNodeDescriptors: List<ErrorTreeNodeDescriptor>
    get() {
      val paths = (if (isDisposed) null else myTree.selectionPaths) ?: return emptyList()
      val result = ArrayList<ErrorTreeNodeDescriptor>()
      for (path in paths) {
        val lastPathNode = path.lastPathComponent as DefaultMutableTreeNode
        val userObject = lastPathNode.userObject
        if (userObject is ErrorTreeNodeDescriptor) {
          result.add(userObject)
        }
      }
      return result
    }

  private fun navigateToSource() {
    val element = selectedNavigatableElement ?: return
    val navigatable = element.navigatable
    if (navigatable.canNavigate()) {
      navigatable.navigate(false)
    }
  }

  private fun popupInvoked(component: Component, x: Int, y: Int) {
    if (myTree.leadSelectionPath == null) {
      return
    }

    val group = DefaultActionGroup()
    if (getData(CommonDataKeys.NAVIGATABLE.name) != null) {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
    }
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY))
    group.add(autoScrollToSourceHandler.createToggleAction())
    addExtraPopupMenuActions(group)
    group.addSeparator()
    group.add(CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this))
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this))
    group.addSeparator()
    group.add(ExportToTextFileToolbarAction(exporterToTextFile))
    val menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMPILER_MESSAGES_POPUP, group)
    menu.component.show(component, x, y)
  }

  protected open fun addExtraPopupMenuActions(group: DefaultActionGroup) {
  }

  fun setProcessController(controller: ProcessController?) {
    processController = controller
  }

  fun stopProcess() {
    processController!!.stopProcess()
  }

  fun canControlProcess(): Boolean {
    return processController != null
  }

  val isProcessStopped: Boolean
    get() = processController!!.isProcessStopped

  open fun close() {
    val messageView = MessageView.getInstance(project)
    messageView.contentManager.getContent(this)?.let {
      messageView.contentManager.removeContent(it, true)
      Disposer.dispose(this)
    }
  }

  fun setProgress(s: @NlsContexts.ProgressText String?, fraction: Float) {
    state.progressText = s
    state.fraction = fraction
    updateProgress()
  }

  fun setProgressText(s: @NlsContexts.ProgressText String?) {
    state.progressText = s
    updateProgress()
  }

  fun setFraction(fraction: Float) {
    state.fraction = fraction
    updateProgress()
  }

  fun clearProgressData() {
    state.clearProgress()
    progressFlow.value = null
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun updateProgress() {
    progressFlow.value = state.progressText
  }

  private fun initProgressPanel() {
    if (progressPanel != null) {
      return
    }

    val progressPanel = JPanel(GridLayout(1, 2))
    this.progressPanel = progressPanel
    progressLabel = JLabel()
    progressPanel.add(progressLabel)
    //JLabel secondLabel = new JLabel();
    //myProgressPanel.add(secondLabel);
    messagePanel.add(progressPanel, BorderLayout.SOUTH)
    messagePanel.validate()
  }

  fun collapseAll() {
    TreeUtil.collapseAll(myTree, 2)
  }

  fun expandAll() {
    val selectionPaths = myTree.selectionPaths
    val leadSelectionPath = myTree.leadSelectionPath
    var row = 0
    while (row < myTree.rowCount) {
      myTree.expandRow(row)
      row++
    }
    selectionPaths?.let {
      // restore selection
      myTree.selectionPaths = it
    }
    leadSelectionPath?.let {
      // scroll to lead selection path
      myTree.scrollPathToVisible(it)
    }
  }

  private fun createToolbarPanel(rerunAction: Runnable?): JPanel {
    val group = DefaultActionGroup()
    val closeMessageViewAction: AnAction = object : CloseTabToolbarAction() {
      override fun actionPerformed(e: AnActionEvent) {
        close()
      }
    }
    if (rerunAction != null) {
      group.add(RerunAction(rerunAction, closeMessageViewAction))
    }
    group.add(StopAction())
    if (canHideWarnings()) {
      group.addSeparator()
      group.add(ShowInfosAction())
      group.add(ShowWarningsAction())
    }
    fillRightToolbarGroup(group)

    //if (myCreateExitAction) {
    //  leftUpdateableActionGroup.add(closeMessageViewAction);
    //}
    //leftUpdateableActionGroup.add(new PreviousOccurenceToolbarAction(this));
    //leftUpdateableActionGroup.add(new NextOccurenceToolbarAction(this));
    //leftUpdateableActionGroup.add(new ExportToTextFileToolbarAction(myExporterToTextFile));
    val actionManager = ActionManager.getInstance()
    leftToolbar = actionManager.createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, group, false)
    leftToolbar!!.targetComponent = messagePanel
    return JBUI.Panels.simplePanel(leftToolbar!!.component)
  }

  protected open fun fillRightToolbarGroup(group: DefaultActionGroup) {
  }

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo = occurrenceNavigatorSupport.goNextOccurence()

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo = occurrenceNavigatorSupport.goPreviousOccurence()

  override fun hasNextOccurence(): Boolean = occurrenceNavigatorSupport.hasNextOccurence()

  override fun hasPreviousOccurence(): Boolean = occurrenceNavigatorSupport.hasPreviousOccurence()

  override fun getNextOccurenceActionName(): @Nls String = occurrenceNavigatorSupport.nextOccurenceActionName

  override fun getPreviousOccurenceActionName(): @Nls String = occurrenceNavigatorSupport.previousOccurenceActionName

  private inner class RerunAction(private val rerunAction: Runnable, private val closeAction: AnAction)
    : DumbAwareAction(IdeBundle.message("action.refresh"), null, AllIcons.Actions.Rerun) {
    override fun actionPerformed(e: AnActionEvent) {
      closeAction.actionPerformed(e)
      rerunAction.run()
    }

    override fun update(event: AnActionEvent) {
      val presentation = event.presentation
      presentation.isEnabled = canControlProcess() && isProcessStopped
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  private inner class StopAction : DumbAwareAction(IdeBundle.messagePointer("action.stop"), AllIcons.Actions.Suspend) {
    override fun actionPerformed(e: AnActionEvent) {
      if (canControlProcess()) {
        stopProcess()
      }
      leftToolbar!!.updateActionsImmediately()
    }

    override fun update(event: AnActionEvent) {
      val presentation = event.presentation
      presentation.isEnabled = canControlProcess() && !isProcessStopped
      presentation.isVisible = canControlProcess()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  protected open fun canHideWarnings(): Boolean = true

  private inner class ShowWarningsAction : ToggleAction(IdeBundle.messagePointer("action.show.warnings"),
                                                          AllIcons.General.ShowWarning), DumbAware {
    override fun isSelected(event: AnActionEvent) = !isHideWarnings

    override fun setSelected(event: AnActionEvent, showWarnings: Boolean) {
      val hideWarnings = !showWarnings
      if (configuration.isHideWarnings != hideWarnings) {
        configuration.isHideWarnings = hideWarnings
        structureModel.invalidateAsync()
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  private inner class ShowInfosAction : ToggleAction(IdeBundle.messagePointer("action.show.infos"),
                                                       AllIcons.General.ShowInfos), DumbAware {
    override fun isSelected(event: AnActionEvent) = !isHideInfos

    override fun setSelected(event: AnActionEvent, showInfos: Boolean) {
      val hideInfos = !showInfos
      if (configuration.isHideInfoMessages != hideInfos) {
        configuration.isHideInfoMessages = hideInfos
        structureModel.invalidateAsync()
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  val isHideWarnings: Boolean
    get() = configuration.isHideWarnings

  val isHideInfos: Boolean
    get() = configuration.isHideInfoMessages

  override fun getGroupChildrenData(groupName: String): List<Any> = errorViewStructure.getGroupChildrenData(groupName)

  override fun removeGroup(name: String) {
    errorViewStructure.removeGroup(name)
  }

  override fun addFixedHotfixGroup(text: String, children: List<SimpleErrorData?>) {
    errorViewStructure.addFixedHotfixGroup(text, children)
  }

  override fun addHotfixGroup(hotfixData: HotfixData, children: List<SimpleErrorData?>) {
    errorViewStructure.addHotfixGroup(hotfixData, children, this)
  }

  override fun reload() {
    structureModel.invalidateAsync()
  }
}

private class MyOccurrenceNavigatorSupport(tree: Tree) : OccurenceNavigatorSupport(tree) {
  override fun createDescriptorForNode(node: DefaultMutableTreeNode): Navigatable? {
    val userObject = node.userObject as? ErrorTreeNodeDescriptor
    return (userObject?.element as? NavigatableErrorTreeElement)?.navigatable
  }

  override fun getNextOccurenceActionName() = IdeBundle.message("action.next.message")

  override fun getPreviousOccurenceActionName() = IdeBundle.message("action.previous.message")
}