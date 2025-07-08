// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.withExplicitClientId
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.impl.StructureViewComposite.StructureViewDescriptor
import com.intellij.ide.structureView.impl.StructureViewState
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.idea.AppMode
import com.intellij.lang.LangBundle
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager.Companion.getInstance
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.impl.ProjectManagerImpl.Companion.isLight
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isTooLargeForIntellijSense
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.psi.PsiElement
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerEvent.ContentOperation
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TimerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@OptIn(FlowPreview::class)
class StructureViewWrapperImpl(
  private val project: Project,
  private val myToolWindow: ToolWindow,
  private val coroutineScope: CoroutineScope,
) : StructureViewWrapper, Disposable {
  private var myFile: VirtualFile? = null
  private var myStructureView: StructureView? = null
  private var myFileEditor: FileEditor? = null
  private var myModuleStructureComponent: ModuleStructureComponent? = null
  private var panels: List<JPanel> = emptyList()
  private var pendingSelectionFunc: AtomicReference<(suspend () -> Unit)?> = AtomicReference()
  private var myFirstRun = true
  private var myActivityCount = 0
  private val pendingRebuild = AtomicBoolean(false)
  private val myActionGroup: DefaultActionGroup = getOrCopyViewOptionsGroup()

  private val rebuildRequests = MutableSharedFlow<RebuildDelay>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    myToolWindow.setTitleActions(listOf(myActionGroup))
    val component = myToolWindow.component

    @Suppress("TestOnlyProblems")
    if (isLight(project)) {
      LOG.error("StructureViewWrapperImpl must be not created for light project.")
    }

    val clientId = ClientId.current

    // to check on the next turn
    val timer = TimerUtil.createNamedTimer("StructureView", REFRESH_TIME) { _ ->
      withExplicitClientId(clientId) { // TODO: IJPL-178436
        if (!component.isShowing) return@withExplicitClientId

        val count = ActivityTracker.getInstance().count
        if (count == myActivityCount) return@withExplicitClientId

        val state = ModalityState.stateForComponent(component)
        if (!ModalityState.current().accepts(state)) return@withExplicitClientId

        val successful = WriteIntentReadAction.compute<Boolean, Throwable> {
          loggedRun("check if update needed") { checkUpdate() }
        }
        if (successful) myActivityCount = count // to check on the next turn
      }
    }
    LOG.debug("timer to check if update needed: add")
    timer.start()
    Disposer.register(this) {
      LOG.debug("timer to check if update needed: remove")
      timer.stop()
    }
    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerEventType) {
        if (toolWindow !== myToolWindow) return
        when (changeType) {
          ToolWindowManagerEventType.ActivateToolWindow,
          ToolWindowManagerEventType.ShowToolWindow -> loggedRun("update file") { checkUpdate() }
          ToolWindowManagerEventType.HideToolWindow -> if (!project.isDisposed) {
            myFile = null
            myFirstRun = true
            rebuildNow("clear a structure on hide")
          }
          else -> {}
        }
      }
    })

    if (component.isShowing) {
      loggedRun("initial structure rebuild") { checkUpdate() }
      scheduleRebuild()
    }
    myToolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
      var currentIndex = -1 // to distinguish event "another tab selected" from "contents were removed and added"
      override fun selectionChanged(event: ContentManagerEvent) {
        if (myStructureView is StructureViewComposite) {
          val views = (myStructureView as StructureViewComposite).structureViews
          views.forEachIndexed { i, view ->
            if (view.title == event.content.tabName) {
              coroutineScope.launch {
                updateHeaderActions(view.structureView)
              }
              if (myToolWindow.contentManager.contentCount == 2 && i != currentIndex && event.operation == ContentOperation.add) {
                if (i != -1) StructureViewEventsCollector.logTabSelected(view)
                currentIndex = i
              }
              return@forEachIndexed
            }
          }
        }
        if (ExperimentalUI.isNewUI()) {
          (myStructureView as? StructureViewComponent)?.let {
            myToolWindow.setAdditionalGearActions(it.dotsActions)
          }
          (myStructureView as? StructureViewComposite)?.structureViews?.forEach {
            (it.structureView as? StructureViewComponent)?.let { sv -> myToolWindow.setAdditionalGearActions(sv.dotsActions) }
          }
        }
      }
    })
    Disposer.register(myToolWindow.contentManager, this)
    PsiStructureViewFactory.EP_NAME.addChangeListener({ clearCaches() }, this)
    StructureViewBuilder.EP_NAME.addChangeListener({ clearCaches() }, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(STRUCTURE_CHANGED, Runnable { clearCaches() })
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosingBeforeSave(project: Project) {
        myToolWindow.contentManager.selectedContent?.tabName
          ?.takeIf { it.isNotEmpty() }
          ?.let { StructureViewState.getInstance(project).selectedTab = it }
      }
    })

    coroutineScope.launch {
      rebuildRequests
        .debounce {
          when (it) {
            RebuildDelay.NOW -> 0L
            RebuildDelay.QUEUE -> REBUILD_TIME
          }
        }
        .collectLatest {
          LOG.debug("starting rebuild request processing")
          // A nested coroutine scope so we can cancel it without terminating the whole collector.
          coroutineScope {
            // Not using simple cancelOnDispose because the content manager may be disposed at any moment,
            // which can create a race condition here.
            // What we want is:
            // 1) be sure that our job is cancelled if it's disposed;
            // 2) not even start the rebuild if it's already disposed.
            // So we end up with pretty much a copy-paste from cancelOnDispose except we use tryRegister.
            val parentDisposable: Disposable = myToolWindow.contentManager
            val thisJob = coroutineContext.job
            val thisDisposable = Disposable {
              thisJob.cancel("disposed")
            }
            thisJob.invokeOnCompletion { e ->
              Disposer.dispose(thisDisposable)
              if (e != null) {
                LOG.debug("finished rebuild request processing with an exception", e)
              }
            }
            if (!Disposer.tryRegister(parentDisposable, thisDisposable)) {
              LOG.debug("canceled rebuild request processing because the tool window content manager is already disposed")
              return@coroutineScope
            }
            runCatching {
              rebuildImpl()
              LOG.debug("finished rebuild request processing successfully")
            }.getOrLogException { e ->
              // catch and hope the next request will succeed, instead of just crashing the whole thing
              LOG.error("failed rebuild request processing", e)
            }
          }
        }
    }
  }

  private fun clearCaches() {
    StructureViewComponent.clearStructureViewState(project)
    if (myStructureView != null) {
      myStructureView!!.disableStoreState()
    }
    rebuildNow("clear caches")
  }
  private fun checkUpdate() {
    if (project.isDisposed) return
    val owner = getFocusOwner()

    val insideToolwindow = SwingUtilities.isDescendingFrom(myToolWindow.component, owner)
                           // On the remote backend focus could be set to IdeFrame
                           // if the on the frontend focus set to a frontend-specific component
                           && (!AppMode.isRemoteDevHost() || owner !is IdeFrame)
    if (insideToolwindow) LOG.debug("inside structure view")
    if (!myFirstRun && (insideToolwindow || JBPopupFactory.getInstance().isPopupActive)) {
      return
    }
    val dataContext = DataManager.getInstance().getDataContext(owner)
    if (WRAPPER_DATA_KEY.getData(dataContext) === this) return
    if (CommonDataKeys.PROJECT.getData(dataContext) !== project) return
    if (insideToolwindow) {
      if (myFirstRun) {
        setFileFromSelectionHistory()
        myFirstRun = false
      }
    }
    else {
      val asyncDataContext = Utils.createAsyncDataContext(dataContext)
      ReadAction.nonBlocking<VirtualFile?> { getTargetVirtualFile(asyncDataContext, owner) }
        .coalesceBy(this, owner)
        .finishOnUiThread(ModalityState.defaultModalityState()) { file: VirtualFile? ->
          val firstRun = myFirstRun
          myFirstRun = false

          coroutineScope.launch {
            if (!myToolWindow.isVisible) {
              return@launch
            }
            else if (file != null) {
              setFile(file)
            }
            else if (firstRun) {
              setFileFromSelectionHistory()
            }
            else {
              setFile(project.serviceAsync<FileEditorManager>().selectedFiles.firstOrNull())
            }
          }
        }
        .submit(AppExecutorUtil.getAppExecutorService())
    }
  }

  private fun setFileFromSelectionHistory() {
    val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
    val firstInHistory = editorManager.getSelectionHistoryList().firstOrNull()
    if (firstInHistory != null) {
      coroutineScope.launch {
        setFile(firstInHistory.first)
      }
    }
  }

  private suspend fun setFile(file: VirtualFile?) {
    if (file?.fileSystem is NonPhysicalFileSystem && PlatformUtils.isIdeaUltimate()) {
      val notInEditor = withContext(Dispatchers.EDT) {
        !project.serviceAsync<FileEditorManager>().selectedFiles.contains(file)
      }
      if (notInEditor) return
    }

    suspend fun setFileAndRebuild() = withContext(Dispatchers.EDT) {
      // myFile access on EDT
      myFile = file
      LOG.debug("show structure for file: ", file)
      scheduleRebuild()
    }

    // File is different
    val differentFiles = withContext(Dispatchers.EDT) { !Comparing.equal(file, myFile) }
    if (differentFiles) {
      setFileAndRebuild()
      return
    }

    // structure view is outdated
    val structureView = myStructureView ?: return
    if (structureView is StructureViewComposite && readAction { structureView.isOutdated }) {
      setFileAndRebuild()
      return
    }

    // tree model is not valid (checking isValid is costly and requires PSI)
    val model = structureView.treeModel
    if (model is TextEditorBasedStructureViewModel && readAction { !model.isValid }) {
      setFileAndRebuild()
      return
    }

    // root element invalid
    val rootTreeElementInvalid = readAction {
      val treeElement = model.root
      val value = treeElement.value
      return@readAction value == null || (value is PsiElement && !value.isValid)
    }
    if (rootTreeElementInvalid) {
      setFileAndRebuild()
      return
    }

    // editor is different
    if (file != null) {
      val editorIsDifferent = withContext(Dispatchers.EDT) {
        project.serviceAsync<FileEditorManager>().getSelectedEditor(file) !== myFileEditor
      }
      if (editorIsDifferent) {
        LOG.debug("Editor is different, rebuilding it")
        setFileAndRebuild()
        return
      }
    }
  }

  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------
  override fun dispose() {
    //we don't really need it
    //rebuild();
  }

  override fun selectCurrentElement(fileEditor: FileEditor?, file: VirtualFile?, requestFocus: Boolean): Boolean {
    //todo [kirillk]
    // this is dirty hack since some bright minds decided to used different TreeUi every time, so selection may be followed
    // by rebuild on completely different instance of TreeUi
    suspend fun selectLater() = withContext(Dispatchers.EDT) {
      if (!Comparing.equal(myFileEditor, fileEditor)) {
        myFile = file
        LOG.debug("replace file on selection: ", file)
        rebuildNow("selected file changed")
      }
      if (myStructureView != null) {
        myStructureView!!.navigateToSelectedElement(requestFocus)
      }
    }
    if (isStructureViewShowing) {
      if (pendingRebuild.get()) {
        pendingSelectionFunc.set(::selectLater)
      }
      else {
        coroutineScope.launch { selectLater() }
      }
    }
    else {
      pendingSelectionFunc.set(::selectLater)
    }

    return true
  }

  private enum class RebuildDelay {
    QUEUE,
    NOW,
  }

  private fun scheduleRebuild() {
    if (!myToolWindow.isVisible) return
    scheduleRebuild(RebuildDelay.QUEUE, "delayed rebuild")
  }

  fun rebuildNow(why: String) {
    scheduleRebuild(RebuildDelay.NOW, why)
  }

  @Deprecated(message = "Every update/rebuild is async now, use scheduleRebuild() to do rebuild at earliest possible time")
  fun rebuild(): Unit = scheduleRebuild()

  private fun scheduleRebuild(delay: RebuildDelay, why: String) {
    LOG.debug("request to rebuild a structure view $delay: $why")
    pendingRebuild.set(true)
    check(rebuildRequests.tryEmit(delay))
  }

  private suspend fun rebuildImpl() {
    if (myToolWindow.isDisposed) return
    val container: Container = myToolWindow.component
    val contentManager = myToolWindow.contentManager
    var wasFocused: Boolean
    withContext(Dispatchers.EDT) {
      wasFocused = UIUtil.isFocusAncestor(container)
      if (myStructureView != null) {
        LOG.debug("Removing all view options on structure view deletion")
        myActionGroup.removeAll()
        myStructureView!!.storeState()
        contentManager.selectedContent?.tabName
          ?.takeIf { it.isNotEmpty() }
          ?.let { StructureViewState.getInstance(project).selectedTab = it }
        Disposer.dispose(myStructureView!!)
        myStructureView = null
        myFileEditor = null
      }
      if (myModuleStructureComponent != null) {
        Disposer.dispose(myModuleStructureComponent!!)
        myModuleStructureComponent = null
      }
      if (!isStructureViewShowing) {
        LOG.debug("updating Structure View on hidden window")
        contentManager.removeAllContents(true)
      }
    }
    if (!isStructureViewShowing) {
      return
    }

    val file = myFile ?: run {
      val selectedFiles = project.serviceAsync<FileEditorManager>().selectedFiles
      if (selectedFiles.isNotEmpty()) selectedFiles[0] else null
    }

    var names = arrayOf<String?>("")
    if (file != null && file.isValid) {
      if (file.isDirectory) {
        val module = readAction {
          if (!ProjectRootsUtil.isModuleContentRoot(file, project)) {
            null
          }
          else {
            ModuleUtilCore.findModuleForFile(file, project)
          }
        }

        if (module != null && !ModuleType.isInternal(module)) {
          withContext(Dispatchers.EDT) {
            myModuleStructureComponent = ModuleStructureComponent(module)
            createSinglePanel(myModuleStructureComponent!!.component!!)
            Disposer.register(this@StructureViewWrapperImpl, myModuleStructureComponent!!)
          }
        }
      }
      else {
        val editor = project.serviceAsync<FileEditorManager>().getSelectedEditor(file)
        val structureViewBuilder = if (editor != null && editor.isValid)
          readAction { editor.structureViewBuilder } else createStructureViewBuilder(file)
        if (structureViewBuilder != null) {
          val structureView = structureViewBuilder.createStructureViewSuspend(editor, project)
          withContext(Dispatchers.EDT) {
            writeIntentReadAction {
              myStructureView = structureView

              myFileEditor = editor
              Disposer.register(this@StructureViewWrapperImpl, structureView)
              val previouslySelectedTab = StructureViewState.getInstance(project).selectedTab
              if (structureView is StructureViewComposite) {
                val views: Array<StructureViewDescriptor> = structureView.structureViews
                names = views.map { it.title }.toTypedArray()
                LOG.trace("[panel-rebuild] Built ${names.size} panels. Names=${names.joinToString(", ", "[", "]") { it ?: "null"}}")
                panels = views.map {
                  if (previouslySelectedTab == it.title) {
                    StructureViewEventsCollector.logBuildStructure(it)
                  }
                  createContentPanel(it.structureView.component)
                }
              }
              else {
                LOG.trace("[panel-rebuild] Build single panel")
                createSinglePanel(structureView.component)
              }
              structureView.restoreState()
              structureView.centerSelectedRow()
            }
          }
        }
      }
    }
    withContext(Dispatchers.EDT) {
      contentManager.removeAllContents(true)
      updateHeaderActions(myStructureView)
      if (myModuleStructureComponent == null && myStructureView == null) {
        val panel: JBPanelWithEmptyText = object : JBPanelWithEmptyText() {
          override fun getBackground(): Color {
            return JBUI.CurrentTheme.ToolWindow.background()
          }
        }
        panel.emptyText.setText(LangBundle.message("panel.empty.text.no.structure"))
        createSinglePanel(panel)
      }
      for (i in panels.indices) {
        val content = ContentFactory.getInstance().createContent(panels[i], names[i], false)
        contentManager.addContent(content)
        val previouslySelectedTab = StructureViewState.getInstance(project).selectedTab
        if (panels.size > 1 && names[i] == previouslySelectedTab) {
          contentManager.setSelectedContent(content)
        }
        if (i == 0 && myStructureView != null) {
          Disposer.register(content, myStructureView!!)
        }
      }

      pendingRebuild.set(false)
      val pendingSelection = pendingSelectionFunc.getAndSet(null)
      if (pendingSelection != null) {
        pendingSelection()
      }

      if (wasFocused) {
        val policy = container.focusTraversalPolicy
        val component = policy?.getDefaultComponent(container)
        if (component != null) IdeFocusManager.getInstance(project).requestFocusInProject(component, project)
      }
    }
  }

  @ApiStatus.Internal
  fun queueUpdate() {
    if (myStructureView is StructureViewComponent) {
      (myStructureView as StructureViewComponent).queueUpdate()
    }
    if (myStructureView is StructureViewComposite) {
      ((myStructureView as StructureViewComposite).selectedStructureView as? StructureViewComponent)?.queueUpdate()
    }
  }

  @ApiStatus.Internal
  fun getStructureView(): StructureView? {
    return myStructureView
  }

  private suspend fun updateHeaderActions(structureView: StructureView?) {
    myActionGroup.removeAll()
    val titleActions: List<AnAction> = if (structureView is StructureViewComponent) {
      if (ExperimentalUI.isNewUI()) {
        readAction { structureView.getViewActions(myActionGroup) }
        listOf(myActionGroup)
      }
      else {
        withContext(Dispatchers.EDT) { structureView.addExpandCollapseActions() }
      }
    }
    else {
      emptyList()
    }

    withContext(Dispatchers.EDT) {
      myToolWindow.setTitleActions(titleActions)
    }
  }

  private fun createSinglePanel(component: JComponent) {
    panels = listOf(createContentPanel(component))
  }

  private fun createContentPanel(component: JComponent): ContentPanel {
    val panel = ContentPanel()
    panel.background = JBUI.CurrentTheme.ToolWindow.background()
    panel.add(component, BorderLayout.CENTER)
    return panel
  }

  private suspend fun createStructureViewBuilder(file: VirtualFile): StructureViewBuilder? {
    if (file.isTooLargeForIntellijSense()) return null
    val providers = getInstance().getProvidersAsync(project, file)
    val provider = (if (providers.isEmpty()) null else providers[0]) ?: return null
    if (provider is StructureViewFileEditorProvider) {
      return readAction { (provider as StructureViewFileEditorProvider).getStructureViewBuilder(project, file) }
    }
    val editor = withContext(Dispatchers.EDT) { provider.createEditor (project, file) }
    return try {
      readAction { editor.structureViewBuilder }
    }
    finally {
      withContext(Dispatchers.EDT) { Disposer.dispose(editor) }
    }
  }

  private val isStructureViewShowing: Boolean
    get() {
      val windowManager = getInstance(project)
      val toolWindow = windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW)
      // it means that window is registered
      return toolWindow != null && toolWindow.isVisible
    }

  private inner class ContentPanel : JPanel(BorderLayout()), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[WRAPPER_DATA_KEY] = this@StructureViewWrapperImpl
      sink[QuickActionProvider.KEY] = myStructureView as? QuickActionProvider
    }
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val STRUCTURE_CHANGED: Topic<Runnable> = Topic("structure view changed", Runnable::class.java,
                                                   Topic.BroadcastDirection.NONE)

    @ApiStatus.Experimental
    @JvmField
    val STRUCTURE_VIEW_TARGET_FILE_KEY: DataKey<Optional<VirtualFile?>> = DataKey.create("STRUCTURE_VIEW_TARGET_FILE_KEY")
    private val STRUCTURE_VIEW_SELECTED_TAB_KEY: Key<String> = Key.create("STRUCTURE_VIEW_SELECTED_TAB")
    private const val STRUCTURE_VIEW_ACTION_GROUP_ID: String = "Structure.ViewOptions"

    private val LOG = Logger.getInstance(StructureViewWrapperImpl::class.java)
    private val WRAPPER_DATA_KEY = DataKey.create<StructureViewWrapper>("WRAPPER_DATA_KEY")
    private const val REFRESH_TIME = 100 // time to check if a context file selection is changed or not
    private const val REBUILD_TIME = 100L // time to wait and merge requests to rebuild a tree model

    private fun getTargetVirtualFile(asyncDataContext: DataContext, focusOwner: Component?): VirtualFile? {
      val explicitlySpecifiedFile = STRUCTURE_VIEW_TARGET_FILE_KEY.getData(asyncDataContext)
      // explicitlySpecifiedFile == null           means no value was specified for this key
      // explicitlySpecifiedFile.isEmpty() == true means target virtual file (and structure view itself) is explicitly suppressed
      if (explicitlySpecifiedFile != null) {
        return explicitlySpecifiedFile.orElse(null)
      }
      val commonFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(asyncDataContext)
      val project = CommonDataKeys.PROJECT.getData(asyncDataContext)
      return when {
        AppMode.isRemoteDevHost() && project != null && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty() -> {
          // In RD, when focus is set to a frontend-component (e.g., tabs, editors, notification tool window),
          // on the backend it can be set to anything, unfortunately.
          // So we fall back to the active editor, or else the structure view may stop updating completely.
          FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }
        commonFiles != null && commonFiles.size == 1 -> commonFiles[0]
        else -> null
      }
    }

    private fun loggedRun(message: String, task: Runnable): Boolean {
      val startTimeNs = System.nanoTime()
      return try {
        if (LOG.isTraceEnabled) LOG.trace("$message: started")
        task.run()
        true
      }
      catch (exception: ProcessCanceledException) {
        LOG.debug(message, ": canceled")
        false
      }
      catch (throwable: Throwable) {
        LOG.warn(message, throwable)
        false
      }
      finally {
        if (LOG.isTraceEnabled) LOG.trace("$message: finished in ${(System.nanoTime() - startTimeNs) / 1000000} ms")
      }
    }

    /**
     * Get a view options action group.
     * It could be either a registered `Structure.ViewOption` action group or a dynamic unregistered action group.
     *  * It will be the registered group if and only if the IDE is running in RemoteDev.
     *    It allows synchronizing action between backend and frontend
     *  * Otherwise, it will be the unregistered dynamic action group.
     *    It forces IDE not to force actions (for CWM)
     */
    private fun getOrCopyViewOptionsGroup(): DefaultActionGroup =
      if (AppMode.isRemoteDevHost())
        ActionManager.getInstance().getAction(STRUCTURE_VIEW_ACTION_GROUP_ID) as DefaultActionGroup
      else DefaultActionGroup(IdeBundle.message("group.view.options"), null, AllIcons.Actions.GroupBy)
        .apply { isPopup = true }
  }

  private fun getFocusOwner(): Component? {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (focusOwner != null || !AppMode.isRemoteDevHost()) return focusOwner
    return FileEditorManager.getInstance(project).selectedTextEditor?.contentComponent
  }
}
