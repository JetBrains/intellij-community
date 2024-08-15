// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewWrapper
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.structureView.impl.StructureViewComposite
import com.intellij.ide.structureView.impl.StructureViewComposite.StructureViewDescriptor
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.lang.LangBundle
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager.Companion.getInstance
import com.intellij.openapi.fileEditor.ex.StructureViewFileEditorProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl.Companion.isLight
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.psi.PsiElement
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.intellij.util.ui.TimerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.event.HierarchyEvent
import java.lang.Runnable
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@OptIn(FlowPreview::class)
class StructureViewWrapperImpl(private val project: Project,
                               private val myToolWindow: ToolWindow,
                               private val coroutineScope: CoroutineScope) : StructureViewWrapper, Disposable {
  private var myFile: VirtualFile? = null
  private var myStructureView: StructureView? = null
  private var myFileEditor: FileEditor? = null
  private var myModuleStructureComponent: ModuleStructureComponent? = null
  private var panels: List<JPanel> = emptyList()
  private var pendingSelectionFunc: AtomicReference<(suspend () -> Unit)?> = AtomicReference()
  private var myFirstRun = true
  private var myActivityCount = 0
  private val pendingRebuild = AtomicBoolean(false)

  private val rebuildRequests = MutableSharedFlow<RebuildDelay>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val component = myToolWindow.component

    @Suppress("TestOnlyProblems")
    if (isLight(project)) {
      LOG.error("StructureViewWrapperImpl must be not created for light project.")
    }

    // to check on the next turn
    val timer = TimerUtil.createNamedTimer("StructureView", REFRESH_TIME) { _ ->
      if (!component.isShowing) return@createNamedTimer

      val count = ActivityTracker.getInstance().count
      if (count == myActivityCount) return@createNamedTimer

      val state = ModalityState.stateForComponent(component)
      if (ModalityState.current().dominates(state)) return@createNamedTimer

      val successful = loggedRun("check if update needed") { checkUpdate() }
      if (successful) myActivityCount = count // to check on the next turn
    }
    LOG.debug("timer to check if update needed: add")
    timer.start()
    Disposer.register(this) {
      LOG.debug("timer to check if update needed: remove")
      timer.stop()
    }
    component.addHierarchyListener { e ->
      if (BitUtil.isSet(e.changeFlags, HierarchyEvent.DISPLAYABILITY_CHANGED.toLong())) {
        val visible = myToolWindow.isVisible
        LOG.debug("displayability changed: $visible")
        if (visible) {
          loggedRun("update file") { checkUpdate() }
          scheduleRebuild()
        }
        else if (!project.isDisposed) {
          myFile = null
          rebuildNow("clear a structure on hide")
        }
      }
    }
    if (component.isShowing) {
      loggedRun("initial structure rebuild") { checkUpdate() }
      scheduleRebuild()
    }
    myToolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        if (myStructureView is StructureViewComposite) {
          val views = (myStructureView as StructureViewComposite).structureViews
          for (view in views) {
            if (view.title == event.content.tabName) {
              coroutineScope.launch {
                updateHeaderActions(view.structureView)
              }
              break
            }
          }
        }
        if (ExperimentalUI.isNewUI() && myStructureView is StructureViewComponent) {
          val additional = (myStructureView as StructureViewComponent).dotsActions
          myToolWindow.setAdditionalGearActions(additional)
        }
      }
    })
    Disposer.register(myToolWindow.contentManager, this)
    PsiStructureViewFactory.EP_NAME.addChangeListener({ clearCaches() }, this)
    StructureViewBuilder.EP_NAME.addChangeListener({ clearCaches() }, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(STRUCTURE_CHANGED, Runnable { clearCaches() })

    coroutineScope.launch {
      rebuildRequests
        .debounce {
          when (it) {
            RebuildDelay.NOW -> 0L
            RebuildDelay.QUEUE -> REBUILD_TIME
          }
        }
        .collectLatest {
          rebuildImpl()
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
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    val insideToolwindow = SwingUtilities.isDescendingFrom(myToolWindow.component, owner)
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
      ReadAction.nonBlocking<VirtualFile?> { getTargetVirtualFile(asyncDataContext) }
        .coalesceBy(this, owner)
        .finishOnUiThread(ModalityState.defaultModalityState()) { file: VirtualFile? ->
          val firstRun = myFirstRun
          myFirstRun = false

          coroutineScope.launch {
            if (file != null) {
              setFile(file)
            }
            else if (firstRun) {
              setFileFromSelectionHistory()
            }
            else {
              setFile(null)
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

  private suspend fun rebuildImpl() = withContext(Dispatchers.EDT) {
    val container: Container = myToolWindow.component
    val wasFocused = UIUtil.isFocusAncestor(container)
    if (myStructureView != null) {
      myStructureView!!.storeState()
      Disposer.dispose(myStructureView!!)
      myStructureView = null
      myFileEditor = null
    }
    if (myModuleStructureComponent != null) {
      Disposer.dispose(myModuleStructureComponent!!)
      myModuleStructureComponent = null
    }
    val contentManager = myToolWindow.contentManager
    contentManager.removeAllContents(true)
    if (!isStructureViewShowing) {
      return@withContext
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
          myModuleStructureComponent = ModuleStructureComponent(module)
          createSinglePanel(myModuleStructureComponent!!.component!!)
          Disposer.register(this@StructureViewWrapperImpl, myModuleStructureComponent!!)
        }
      }
      else {
        val editor = project.serviceAsync<FileEditorManager>().getSelectedEditor(file)
        val structureViewBuilder = if (editor != null && editor.isValid)
          readAction { editor.structureViewBuilder } else createStructureViewBuilder(file)
        if (structureViewBuilder != null) {
          writeIntentReadAction {
            val structureView = structureViewBuilder.createStructureView(editor, project)
            myStructureView = structureView

            myFileEditor = editor
            Disposer.register(this@StructureViewWrapperImpl, structureView)
            if (structureView is StructureViewComposite) {
              val views: Array<StructureViewDescriptor> = structureView.structureViews
              names = views.map { it.title }.toTypedArray()
              panels = views.map { createContentPanel(it.structureView.component) }
            }
            else {
              createSinglePanel(structureView.component)
            }
            structureView.restoreState()
            structureView.centerSelectedRow()
          }
        }
      }
    }
    updateHeaderActions(myStructureView)
    if (myModuleStructureComponent == null && myStructureView == null) {
      val panel: JBPanelWithEmptyText = object : JBPanelWithEmptyText() {
        override fun getBackground(): Color {
          return UIUtil.getTreeBackground()
        }
      }
      panel.emptyText.setText(LangBundle.message("panel.empty.text.no.structure"))
      createSinglePanel(panel)
    }
    for (i in panels.indices) {
      val content = ContentFactory.getInstance().createContent(panels[i], names[i], false)
      contentManager.addContent(content)
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

  private suspend fun updateHeaderActions(structureView: StructureView?) {
    val titleActions: List<AnAction> = if (structureView is StructureViewComponent) {
      if (ExperimentalUI.isNewUI()) {
        readAction { listOf(structureView.viewActions) }
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
    panel.background = UIUtil.getTreeBackground()
    panel.add(component, BorderLayout.CENTER)
    return panel
  }

  private suspend fun createStructureViewBuilder(file: VirtualFile): StructureViewBuilder? {
    if (file.length > PersistentFSConstants.getMaxIntellisenseFileSize()) return null
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

    private val LOG = Logger.getInstance(StructureViewWrapperImpl::class.java)
    private val WRAPPER_DATA_KEY = DataKey.create<StructureViewWrapper>("WRAPPER_DATA_KEY")
    private const val REFRESH_TIME = 100 // time to check if a context file selection is changed or not
    private const val REBUILD_TIME = 100L // time to wait and merge requests to rebuild a tree model

    private fun getTargetVirtualFile(asyncDataContext: DataContext): VirtualFile? {
      val explicitlySpecifiedFile = STRUCTURE_VIEW_TARGET_FILE_KEY.getData(asyncDataContext)
      // explicitlySpecifiedFile == null           means no value was specified for this key
      // explicitlySpecifiedFile.isEmpty() == true means target virtual file (and structure view itself) is explicitly suppressed
      if (explicitlySpecifiedFile != null) {
        return explicitlySpecifiedFile.orElse(null)
      }
      val commonFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(asyncDataContext)
      return if (commonFiles != null && commonFiles.size == 1) commonFiles[0] else null
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
  }
}
