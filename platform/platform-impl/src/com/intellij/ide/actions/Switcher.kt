// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.OpenInRightSplitAction.Companion.openInRightSplit
import com.intellij.ide.actions.SwitcherLogger.NAVIGATED
import com.intellij.ide.actions.SwitcherLogger.NAVIGATED_INDEXES
import com.intellij.ide.actions.SwitcherLogger.NAVIGATED_ORIGINAL_INDEXES
import com.intellij.ide.actions.SwitcherLogger.SHOWN_TIME_ACTIVITY
import com.intellij.ide.actions.SwitcherSpeedSearch.Companion.installOn
import com.intellij.ide.actions.ui.JBListWithOpenInRightSplit
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.fileEditor.impl.getOpenMode
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.LightEditActionFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.PopupUpdateProcessorBase
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.SwingTextTrimmer
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.await
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.math.max
import kotlin.math.min

/**
 * @author Konstantin Bulenkov
 */
object Switcher : BaseSwitcherAction(null) {
  @ApiStatus.Internal
  val SWITCHER_KEY: Key<SwitcherPanel> = Key.create("SWITCHER_KEY")

  @ApiStatus.Internal
  class SwitcherPanel(
    val project: Project,
    val title: @Nls String,
    event: InputEvent?,
    onlyEditedFiles: Boolean?,
    forward: Boolean,
  ) : BorderLayoutPanel(), UiDataProvider, QuickSearchComponent, Disposable {
    val popup: JBPopup?
    val activity = SHOWN_TIME_ACTIVITY.started(project)
    private var navigationData: SwitcherLogger.NavigationData? = null
    internal val toolWindows: JBList<SwitcherListItem>
    val files: JBList<SwitcherVirtualFile>
    val cbShowOnlyEditedFiles: JCheckBox?
    private val pathLabel: JLabel = HintUtil.createAdComponent(
      " ",
      if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Advertiser.border()
      else JBUI.Borders.compound(
        JBUI.Borders.customLineTop(JBUI.CurrentTheme.Advertiser.borderColor()),
        JBUI.CurrentTheme.Advertiser.border()
      ),
      SwingConstants.LEFT
    )

    // false - Switcher, true - Recent files / Recently changed files
    val recent: Boolean = onlyEditedFiles != null

    // false - auto closeable on modifier key release, true - default popup
    val pinned: Boolean

    private val onKeyRelease: SwitcherKeyReleaseListener
    private val speedSearch: SwitcherSpeedSearch?
    private var hint: JBPopup? = null

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.PROJECT] = project
      sink[PlatformCoreDataKeys.SELECTED_ITEM] = if (files.isSelectionEmpty) null else files.selectedValuesList.singleOrNull()?.file
      sink[PlatformDataKeys.SPEED_SEARCH_TEXT] = if (speedSearch?.isPopupActive == true) speedSearch.enteredPrefix else null
      sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = run {
        if (files.isSelectionEmpty) return@run null
        files.selectedValuesList.map(SwitcherVirtualFile::file).takeIf { it.isNotEmpty() }?.toTypedArray()
      }
    }

    init {
      onKeyRelease = SwitcherKeyReleaseListener(if (recent) null else event) { e ->
        ActionUtil.performInputEventHandlerWithCallbacks(e) {
          navigate(e)
        }
      }
      pinned = !onKeyRelease.isEnabled
      val onlyEdited = true == onlyEditedFiles
      speedSearch = if (recent && Registry.`is`("ide.recent.files.speed.search")) installOn(this) else null
      cbShowOnlyEditedFiles = if (!recent || !Experiments.getInstance().isFeatureEnabled("recent.and.edited.files.together")) {
        null
      }
      else {
        JCheckBox(IdeBundle.message("recent.files.checkbox.label"))
      }

      val renderer = SwitcherListRenderer(this)
      val windows = renderer.toolWindows
      val showMnemonics = speedSearch == null || Registry.`is`("ide.recent.files.tool.window.mnemonics")
      if (showMnemonics || Registry.`is`("ide.recent.files.tool.window.sort.by.mnemonics")) {
        updateMnemonics(windows, showMnemonics)
      }
      // register custom actions as soon as possible to block overridden actions
      if (pinned) {
        registerAction(ActionUtil.getShortcutSet("PopupMenu-return")) { navigate(it) }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_EDITOR_ESCAPE)) { hideSpeedSearchOrPopup() }
        registerAction(ActionUtil.getShortcutSet("DeleteRecentFiles")) { closeTabOrToolWindow() }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_NEW_WINDOW)) { navigate(it) }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)) { navigate(it) }
      }
      else {
        registerAction("ENTER") { navigate(it) }
        registerAction("ESCAPE") { hideSpeedSearchOrPopup() }
        registerAction("DELETE", "BACK_SPACE") { closeTabOrToolWindow() }
        registerSwingAction(ListActions.Up.ID, "KP_UP", "UP")
        registerSwingAction(ListActions.Down.ID, "KP_DOWN", "DOWN")
        registerSwingAction(ListActions.Left.ID, "KP_LEFT", "LEFT")
        registerSwingAction(ListActions.Right.ID, "KP_RIGHT", "RIGHT")
        registerSwingAction(ListActions.PageUp.ID, "PAGE_UP")
        registerSwingAction(ListActions.PageDown.ID, "PAGE_DOWN")
      }
      if (speedSearch == null || Registry.`is`("ide.recent.files.tool.window.mnemonics")) {
        for (window in windows) {
          registerToolWindowAction(window)
        }
      }
      border = JBUI.Borders.empty()
      pathLabel.putClientProperty(SwingTextTrimmer.KEY, SwingTextTrimmer.THREE_DOTS_AT_LEFT)
      val header = JPanel(HorizontalLayout(5))
      val titleLabel = RelativeFont.BOLD.install(JLabel(title))
      header.add(HorizontalLayout.LEFT, titleLabel)
      if (ExperimentalUI.isNewUI()) {
        background = JBUI.CurrentTheme.Popup.BACKGROUND
        titleLabel.border = PopupUtil.getComplexPopupVerticalHeaderBorder()
        header.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
        header.border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                              PopupUtil.getComplexPopupHorizontalHeaderBorder())
      }
      else {
        background = JBColor.background()
        header.background = JBUI.CurrentTheme.Popup.headerBackground(false)
        header.border = JBUI.Borders.empty(4, 8)
      }
      if (cbShowOnlyEditedFiles != null) {
        cbShowOnlyEditedFiles.isOpaque = false
        cbShowOnlyEditedFiles.isFocusable = false
        cbShowOnlyEditedFiles.isSelected = onlyEdited
        cbShowOnlyEditedFiles.addItemListener(ItemListener(::updateFilesByCheckBox))
        header.add(HorizontalLayout.RIGHT, cbShowOnlyEditedFiles)
        WindowMoveListener(header).installTo(header)
        val shortcuts = KeymapUtil.getActiveKeymapShortcuts("SwitcherRecentEditedChangedToggleCheckBox")
        if (shortcuts.shortcuts.isNotEmpty()) {
          val label = JLabel(KeymapUtil.getShortcutsText(shortcuts.shortcuts))
          label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
          header.add(HorizontalLayout.RIGHT, label)
        }
      }

      val switcherListItems = mutableListOf<SwitcherListItem>()
      for (element in windows.sortedWith { o1, o2 ->
        val m1 = o1.mnemonic
        val m2 = o2.mnemonic
        if (m1 == null) (if (m2 == null) 0 else 1) else if (m2 == null) -1 else m1.compareTo(m2)
      }) {
        switcherListItems.add(element)
      }
      if (pinned && !windows.isEmpty()) {
        switcherListItems.add(SwitcherRecentLocations(this))
      }

      if (!showMnemonics) {
        for (window in windows) {
          window.mnemonic = null
        }
      }

      val twModel = CollectionListModel(switcherListItems, true)
      toolWindows = JBList(speedSearch?.wrap(twModel) ?: twModel)
      toolWindows.visibleRowCount = toolWindows.itemsCount
      toolWindows.border = JBUI.Borders.empty(5, 0)
      toolWindows.selectionMode = if (pinned) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      toolWindows.accessibleContext.accessibleName = IdeBundle.message("recent.files.accessible.tool.window.list")
      toolWindows.setEmptyText(IdeBundle.message("recent.files.tool.window.list.empty.text"))
      toolWindows.setCellRenderer(renderer)
      toolWindows.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true)
      toolWindows.addKeyListener(onKeyRelease)
      PopupUtil.applyNewUIBackground(toolWindows)
      ScrollingUtil.installActions(toolWindows)
      ListHoverListener.DEFAULT.addTo(toolWindows)

      val clickListener: ClickListener = object : ClickListener() {
        override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
          if (pinned && (e.isControlDown || e.isMetaDown || e.isShiftDown)) return false
          val source = e.source
          if (source is JList<*>) {
            if (source.selectedIndex == -1 && source.anchorSelectionIndex != -1) {
              source.selectedIndex = source.anchorSelectionIndex
            }
            if (source.selectedIndex != -1) {
              ActionUtil.performInputEventHandlerWithCallbacks(e) {
                navigate(e)
              }
            }
          }
          return true
        }
      }
      clickListener.installOn(toolWindows)

      val filesModel = CollectionListModel<SwitcherVirtualFile>()
      val filesToShow = getFilesToShow(
        project = project,
        onlyEdited = onlyEdited,
        toolWindowsCount = toolWindows.itemsCount,
        pinned = recent,
      )
      resetListModelAndUpdateNames(filesModel, filesToShow)
      val filesSelectionListener = object : ListSelectionListener {
        override fun valueChanged(e: ListSelectionEvent) {
          if (e.valueIsAdjusting) {
            return
          }

          updatePathLabel()
          val hint = hint
          val popupUpdater = if (hint == null || !hint.isVisible) null else hint.getUserData(PopupUpdateProcessorBase::class.java)
          popupUpdater?.updatePopup(CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContext(this@SwitcherPanel)))
        }
      }

      files = JBListWithOpenInRightSplit.createListWithOpenInRightSplitter(speedSearch?.wrap(filesModel) ?: filesModel, null)
      files.visibleRowCount = files.itemsCount
      files.selectionMode = if (pinned) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      files.accessibleContext.accessibleName = IdeBundle.message("recent.files.accessible.file.list")
      files.setEmptyText(IdeBundle.message("recent.files.file.list.empty.text"))
      toolWindows.selectionModel.addListSelectionListener(filesSelectionListener)
      files.selectionModel.addListSelectionListener(filesSelectionListener)
      files.setCellRenderer(renderer)
      files.border = JBUI.Borders.empty(5, 0)
      files.addKeyListener(onKeyRelease)
      PopupUtil.applyNewUIBackground(files)
      ScrollingUtil.installActions(files)
      ListHoverListener.DEFAULT.addTo(files)
      clickListener.installOn(files)
      if (filesModel.size > 0) {
        val selectionIndex = getFilesSelectedIndex(project, files, forward)
        files.setSelectedIndex(if (selectionIndex > -1) selectionIndex else 0)
      }
      else {
        ScrollingUtil.ensureSelectionExists(toolWindows)
      }
      addToTop(header)
      addToBottom(pathLabel)
      addToCenter(SwitcherScrollPane(files, true))
      if (!windows.isEmpty()) {
        addToLeft(SwitcherScrollPane(toolWindows, false))
      }
      if (speedSearch != null) {
        // copy a speed search listener from the panel to the lists
        val listener = keyListeners.lastOrNull()
        files.addKeyListener(listener)
        toolWindows.addKeyListener(listener)
      }
      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(this,
                                                                       if (!files.isEmpty || toolWindows.isEmpty) files else toolWindows)
        .setResizable(pinned)
        .setNormalWindowLevel(pinned && StartupUiUtil.isWaylandToolkit()) // On Wayland, only "normal" windows can be moved smoothly at the moment
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setDimensionServiceKey(if (pinned) project else null, if (pinned) "SwitcherDM" else null, false)
        .setCancelKeyEnabled(false)
        .createPopup()
      Disposer.register(popup, this)
      if (pinned) {
        popup.setMinimumSize(JBDimension(if (windows.isEmpty()) 300 else 500, 200))
      }
      isFocusCycleRoot = true
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
      SwitcherListFocusAction(files, toolWindows, ListActions.Left.ID)
      SwitcherListFocusAction(toolWindows, files, ListActions.Right.ID)
      IdeEventQueue.getInstance().popupManager.closeAllPopups(false)
      val old = project.getUserData(SWITCHER_KEY)
      old?.cancel()
      project.putUserData(SWITCHER_KEY, this)
      popup.showCenteredInCurrentWindow(project)

      if (Registry.`is`("highlighting.passes.cache")) {
        HighlightingPassesCache.getInstance(project).schedule(getNotOpenedRecentFiles())
      }
    }

    private fun getNotOpenedRecentFiles(): List<VirtualFile> {
      val recentFiles = EditorHistoryManager.getInstance(project).fileList
      val openFiles = FileEditorManager.getInstance(project).openFiles
      return recentFiles.subtract(openFiles.toSet()).toList()
    }

    override fun dispose() {
      project.putUserData(SWITCHER_KEY, null)
      activity.finished {
        buildList {
          NAVIGATED.with(navigationData != null && navigationData!!.navigationIndexes.isNotEmpty())
          if (navigationData != null) {
            NAVIGATED_ORIGINAL_INDEXES.with(navigationData!!.navigationOriginalIndexes)
            NAVIGATED_INDEXES.with(navigationData!!.navigationIndexes)
          }
        }
      }
    }

    val isOnlyEditedFilesShown: Boolean
      get() = cbShowOnlyEditedFiles != null && cbShowOnlyEditedFiles.isSelected
    val isSpeedSearchPopupActive: Boolean
      get() = speedSearch != null && speedSearch.isPopupActive

    override fun registerHint(h: JBPopup) {
      if (hint != null && hint!!.isVisible && hint !== h) {
        hint!!.cancel()
      }
      hint = h
    }

    override fun unregisterHint() {
      hint = null
    }

    private fun updatePathLabel() {
      val values = selectedList?.selectedValuesList
      val statusText = values?.singleOrNull()?.statusText
      pathLabel.text = if (statusText.isNullOrEmpty()) " " else statusText
    }

    private fun updateMnemonics(windows: List<SwitcherToolWindow>, showMnemonics: Boolean) {
      val keymap: MutableMap<String?, SwitcherToolWindow?> = HashMap(windows.size)
      keymap[onKeyRelease.forbiddenMnemonic] = null
      addForbiddenMnemonics(keymap, "SwitcherForward")
      addForbiddenMnemonics(keymap, "SwitcherBackward")
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
      val otherTW: MutableList<SwitcherToolWindow> = ArrayList()
      for (window in windows) {
        val index = ActivateToolWindowAction.Manager.getMnemonicForToolWindow(window.window.id)
        val indexShortcut = getIndexShortcut(index - '0'.code) // can never be null here in the current implementation
        if (index < '0'.code || index > '9'.code || indexShortcut == null || !addShortcut(keymap, window, indexShortcut)) {
          otherTW.add(window)
        }
      }
      if (!showMnemonics && !Registry.`is`("ide.recent.files.tool.window.sort.by.automatic.mnemonics")) return
      var i = 0
      for (window in otherTW) {
        if (addSmartShortcut(window, keymap)) {
          continue
        }
        while (true) {
          val indexShortcut = getIndexShortcut(i)
          if (indexShortcut == null) {
            break // ran out of shortcuts
          }
          else if (addShortcut(keymap, window, indexShortcut)) {
            ++i // added successfully, should use the next shortcut for the next window
            break
          }
          else {
            ++i // shortcut not suitable, let's try the next one
          }
        }
      }
    }

    private fun addForbiddenMnemonics(keymap: MutableMap<String?, SwitcherToolWindow?>, actionId: String) {
      for (shortcut in ActionUtil.getShortcutSet(actionId).shortcuts) {
        if (shortcut is KeyboardShortcut) {
          keymap[onKeyRelease.getForbiddenMnemonic(shortcut.firstKeyStroke)] = null
        }
      }
    }

    private fun closeTabOrToolWindow() {
      if (speedSearch != null && speedSearch.isPopupActive) {
        speedSearch.updateEnteredPrefix()
        return
      }
      val selectedList: JList<out SwitcherListItem>? = selectedList
      val selected = selectedList!!.selectedIndices
      Arrays.sort(selected)
      var selectedIndex = 0
      for (i in selected.indices.reversed()) {
        selectedIndex = selected[i]
        val item = selectedList.model.getElementAt(selectedIndex)
        if (item is SwitcherVirtualFile) {
          val virtualFile: VirtualFile = item.file
          val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
          val window = findAppropriateWindow(item.window)
          if (window == null) {
            fileEditorManager.closeFile(virtualFile, false, false)
          }
          else {
            fileEditorManager.closeFile(virtualFile, window)
          }
          ListUtil.removeItem(files.model, selectedIndex)
          if (item.window == null) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile)
          }
        }
        else item?.close(this)
      }
      if (files === selectedList) {
        val size = files.itemsCount
        if (size > 0) {
          val index = min(max(selectedIndex, 0), size - 1)
          files.selectedIndex = index
          files.ensureIndexIsVisible(index)
        }
        else {
          toolWindows.requestFocusInWindow()
        }
      }
    }

    fun cancel() {
      popup!!.cancel()
    }

    private fun hideSpeedSearchOrPopup() {
      if (speedSearch == null || !speedSearch.isPopupActive) {
        cancel()
      }
      else {
        speedSearch.hidePopup()
      }
    }

    fun go(forward: Boolean) {
      val selected = selectedList
      var list = selected
      var index = list!!.selectedIndex
      if (forward) index++ else index--
      if (forward && index >= list.itemsCount || !forward && index < 0) {
        if (!toolWindows.isEmpty && !files.isEmpty) {
          list = if (list === files) toolWindows else files
        }
        index = if (forward) 0 else list.itemsCount - 1
      }
      list.selectedIndex = index
      list.ensureIndexIsVisible(index)
      if (selected !== list) {
        IdeFocusManager.findInstanceByComponent(list).requestFocus(list, true)
      }
    }

    fun goForward() {
      go(true)
    }

    fun goBack() {
      go(false)
    }

    private val selectedList: JBList<out SwitcherListItem>?
      get() = getSelectedList(files)

    private fun getSelectedList(preferable: JBList<out SwitcherListItem>?): JBList<out SwitcherListItem>? {
      return if (files.hasFocus()) files else if (toolWindows.hasFocus()) toolWindows else preferable
    }

    private fun updateFilesByCheckBox(event: ItemEvent) {
      val onlyEdited = ItemEvent.SELECTED == event.stateChange
      val listWasSelected = files.selectedIndex != -1
      val filesToShow = getFilesToShow(project, onlyEdited, toolWindows.itemsCount, recent)
      resetListModelAndUpdateNames(
        (files.model as FilteringListModel<SwitcherVirtualFile>).originalModel as CollectionListModel<SwitcherVirtualFile>, filesToShow)
      val selectionIndex = getFilesSelectedIndex(project, files, true)
      if (selectionIndex > -1 && listWasSelected) {
        files.selectedIndex = selectionIndex
      }
      files.revalidate()
      files.repaint()
      // refresh the Recent Locations item
      val toolWindowsModel = toolWindows.model
      if (toolWindowsModel is NameFilteringListModel<*>) {
        (toolWindowsModel as NameFilteringListModel<*>).refilter()
      }
      toolWindows.repaint()
    }

    private fun resetListModelAndUpdateNames(model: CollectionListModel<SwitcherVirtualFile>, items: List<SwitcherVirtualFile>) {
      for (datum in items) {
        datum.mainText = datum.file.presentableName
      }
      model.removeAll()
      model.addAll(0, items)

      class ListItemData(
        val item: SwitcherVirtualFile,
        val mainText: String,
        val statusText: String,
        val backgroundColor: Color?,
        val foregroundTextColor: Color?,
      )
      ReadAction.nonBlocking<List<ListItemData>> {
        items.map {
          ListItemData(item = it,
                       mainText = VfsPresentationUtil.getUniquePresentableNameForUI(it.project, it.file),
                       statusText = FileUtil.getLocationRelativeToUserHome((it.file.parent ?: it.file).presentableUrl),
                       backgroundColor = VfsPresentationUtil.getFileBackgroundColor(it.project, it.file),
                       foregroundTextColor = FileStatusManager.getInstance(it.project).getStatus(it.file).color)
        }
      }
        .expireWith(this)
        .finishOnUiThread(ModalityState.any()) { list ->
          for (data in list) {
            data.item.mainText = data.mainText
            data.item.statusText = data.statusText
            data.item.backgroundColor = data.backgroundColor
            data.item.foregroundTextColor = data.foregroundTextColor
          }
          files.invalidate()
          files.repaint()
          updatePathLabel()
        }
        .submit(AppExecutorUtil.getAppExecutorService())
    }

    fun navigate(e: InputEvent?) {
      val mode = if (e == null) FileEditorManagerImpl.OpenMode.DEFAULT else getOpenMode(e)
      val values: List<*> = selectedList!!.selectedValuesList
      val searchQuery = speedSearch?.enteredPrefix

      navigationData = createNavigationData(values)

      cancel()
      if (values.isEmpty()) {
        tryToOpenFileSearch(e, searchQuery)
      }
      else if (values[0] is SwitcherVirtualFile) {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
          {
            val manager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
            var splitWindow: EditorWindow? = null
            for (value in values) {
              if (value is SwitcherVirtualFile) {
                val file = value.file
                if (mode === FileEditorManagerImpl.OpenMode.RIGHT_SPLIT) {
                  if (splitWindow == null) {
                    splitWindow = openInRightSplit(project = project, file = file, element = null, requestFocus = true)
                  }
                  else {
                    manager.openFile(file, splitWindow, FileEditorOpenOptions().withRequestFocus())
                  }
                }
                else if (mode == FileEditorManagerImpl.OpenMode.NEW_WINDOW) {
                  manager.openFileInNewWindow(file)
                }
                else if (value.window != null) {
                  val editorWindow = findAppropriateWindow(value.window)
                  if (editorWindow != null) {
                    manager.openFileImpl2(window = editorWindow, file = file, options = FileEditorOpenOptions(requestFocus = true))
                  }
                }
                else {
                  val settings = UISettings.getInstance().state
                  val oldValue = settings.reuseNotModifiedTabs
                  settings.reuseNotModifiedTabs = false
                  manager.openFile(
                    file = file,
                    window = null,
                    options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true),
                  )
                  if (LightEdit.owns(project)) {
                    LightEditFeatureUsagesUtil.logFileOpen(project, OpenPlace.RecentFiles)
                  }
                  if (oldValue) {
                    settings.reuseNotModifiedTabs = true
                  }
                }
              }
            }
          },
          ModalityState.current(),
        )
      }
      else if (values[0] is SwitcherListItem) {
        val item = values[0] as SwitcherListItem
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown({ item.navigate(this, mode) }, ModalityState.current())
      }
    }

    private fun createNavigationData(values: List<*>): SwitcherLogger.NavigationData? {
      if (selectedList != files) return null

      val filteringListModel = files.model as? FilteringListModel<SwitcherVirtualFile> ?: return null
      val collectionListModel = filteringListModel.originalModel as? CollectionListModel<SwitcherVirtualFile> ?: return null
      val originalIndexes = values.filterIsInstance<SwitcherVirtualFile>().map { collectionListModel.getElementIndex(it) }
      val navigatedIndexes = values.filterIsInstance<SwitcherVirtualFile>().map { filteringListModel.getElementIndex(it) }

      return SwitcherLogger.NavigationData(originalIndexes, navigatedIndexes)
    }

    private fun tryToOpenFileSearch(e: InputEvent?, fileName: String?) {
      if (fileName.isNullOrEmpty()) {
        return
      }

      val gotoAction = ActionManager.getInstance().getAction("GotoFile")
      if (gotoAction == null) {
        return
      }

      cancel()
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
        val focusDC = DataManager.getInstance().dataContextFromFocusAsync.await()
        val dataContext = CustomizedDataContext.withSnapshot(focusDC) { sink ->
          sink[PlatformDataKeys.PREDEFINED_TEXT] = fileName
        }
        val event = AnActionEvent(e, dataContext, "Switcher",
                                  gotoAction.templatePresentation.clone(),
                                  ActionManager.getInstance(), 0)
        blockingContext {
          ActionUtil.performActionDumbAwareWithCallbacks(gotoAction, event)
        }
      }
    }

    private fun registerAction(vararg keys: String, action: (InputEvent?) -> Unit) {
      registerAction(onKeyRelease.getShortcuts(*keys), action)
    }

    private fun registerAction(shortcuts: ShortcutSet, action: (InputEvent?) -> Unit) {
      // ignore an empty shortcut set
      if (shortcuts.shortcuts.isEmpty()) {
        return
      }

      LightEditActionFactory.create { event ->
        if (popup != null && popup.isVisible) action(event.inputEvent)
      }.registerCustomShortcutSet(shortcuts, this, this)
    }

    private fun registerSwingAction(id: @NonNls String, vararg keys: String) {
      registerAction(*keys) { SwingActionDelegate.performAction(id, getSelectedList(null)) }
    }

    private fun registerToolWindowAction(window: SwitcherToolWindow) {
      val mnemonic = window.mnemonic
      if (!mnemonic.isNullOrEmpty()) {
        registerAction(
          when {
            speedSearch == null -> onKeyRelease.getShortcuts(mnemonic)
            SystemInfo.isMac -> CustomShortcutSet.fromString("alt $mnemonic", "alt control $mnemonic")
            else -> CustomShortcutSet.fromString("alt $mnemonic")
          }) {
          cancel()
          window.window.activate(null, true, true)
        }
      }
    }

    companion object {
      fun getFilesSelectedIndex(project: Project, filesList: JList<*>, forward: Boolean): Int {
        val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
        val currentWindow = editorManager.currentWindow
        val currentFile = currentWindow?.selectedFile
        val model = filesList.model
        if (forward) {
          for (i in 0 until model.size) {
            if (!isTheSameTab(currentWindow, currentFile, model.getElementAt(i))) {
              return i
            }
          }
        }
        else {
          for (i in model.size - 1 downTo 0) {
            if (!isTheSameTab(currentWindow, currentFile, model.getElementAt(i))) {
              return i
            }
          }
        }
        return -1
      }

      @TestOnly
      @JvmStatic
      fun getFilesToShowForTest(project: Project): List<VirtualFile> {
        return getFilesToShow(project = project, onlyEdited = false, toolWindowsCount = 10, pinned = true).map(SwitcherVirtualFile::file)
      }

      @TestOnly
      fun getFilesSelectedIndexForTest(project: Project, goForward: Boolean): Int {
        return getFilesSelectedIndex(
          project = project,
          filesList = JBList(getFilesToShow(project = project, onlyEdited = false, toolWindowsCount = 10, pinned = true)),
          forward = goForward,
        )
      }
    }
  }
}

private const val SWITCHER_ELEMENTS_LIMIT: Int = 30

private fun getFilesToShow(project: Project, onlyEdited: Boolean, toolWindowsCount: Int, pinned: Boolean): List<SwitcherVirtualFile> {
  val filesData = ArrayList<SwitcherVirtualFile>()
  val editors = ArrayList<SwitcherVirtualFile>()
  val addedFiles = LinkedHashSet<VirtualFile>()
  if (!pinned) {
    for (pair in (FileEditorManager.getInstance(project) as FileEditorManagerImpl).getSelectionHistoryList()) {
      editors.add(SwitcherVirtualFile(project = project, file = pair.first, window = pair.second))
    }

    for (editor in editors) {
      addedFiles.add(editor.file)
      filesData.add(editor)
      if (filesData.size >= SWITCHER_ELEMENTS_LIMIT) {
        break
      }
    }
  }

  if (filesData.size > 1) {
    return filesData
  }

  val filesForInit = if (onlyEdited) IdeDocumentHistory.getInstance(project).changedFiles else getRecentFiles(project)
  if (!filesForInit.isEmpty()) {
    val editorFileCount = editors.asSequence().map { it.file }.distinct().count()
    val maxFiles = max(editorFileCount, filesForInit.size)
    val minIndex = if (pinned) 0 else filesForInit.size - toolWindowsCount.coerceAtMost(maxFiles)
    for (i in filesForInit.size - 1 downTo minIndex) {
      val info = SwitcherVirtualFile(project = project, file = filesForInit[i], window = null)
      var add = true
      if (pinned) {
        for (fileInfo in filesData) {
          if (fileInfo.file == info.file) {
            add = false
            break
          }
        }
      }
      if (add) {
        if (addedFiles.add(info.file)) {
          filesData.add(info)
        }
      }
    }
  }

  if (editors.size == 1 && (filesData.isEmpty() || editors[0].file != filesData[0].file)) {
    if (addedFiles.add(editors[0].file)) {
      filesData.add(0, editors[0])
    }
  }
  return filesData
}

private fun isTheSameTab(currentWindow: EditorWindow?, currentFile: VirtualFile?, element: Any): Boolean {
  val svf = if (element is SwitcherVirtualFile) element else null
  return svf != null && svf.file == currentFile && (svf.window == null || svf.window == currentWindow)
}

private fun getRecentFiles(project: Project): List<VirtualFile> {
  val recentFiles = EditorHistoryManager.getInstance(project).fileList
  val openFiles = FileEditorManager.getInstance(project).openFiles
  val recentFilesSet = HashSet(recentFiles)
  val openFilesSet = openFiles.toHashSet()

  // add missing FileEditor tabs right after the last one, that is available via "Recent Files"
  var index = 0
  for (i in recentFiles.indices) {
    if (openFilesSet.contains(recentFiles[i])) {
      index = i
      break
    }
  }
  val result = ArrayList(recentFiles)
  result.addAll(index, openFiles.filter { !recentFilesSet.contains(it) })
  return result
}

private fun addShortcut(keymap: MutableMap<String?, SwitcherToolWindow?>, window: SwitcherToolWindow, shortcut: String): Boolean {
  if (keymap.containsKey(shortcut)) return false
  keymap[shortcut] = window
  window.mnemonic = shortcut
  return true
}

private fun addSmartShortcut(window: SwitcherToolWindow, keymap: MutableMap<String?, SwitcherToolWindow?>): Boolean {
  val title = window.mainText
  if (StringUtil.isEmpty(title)) return false
  for (c in title) {
    if (Character.isUpperCase(c) && addShortcut(keymap, window, c.toString())) {
      return true
    }
  }
  return false
}

private fun getIndexShortcut(index: Int): String? {
  if (index !in 0..35) return null
  return Strings.toUpperCase(index.toString(radix = (index + 1).coerceIn(2..36)))
}

private fun findAppropriateWindow(window: EditorWindow?): EditorWindow? {
  if (window == null) return null
  if (UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE) {
    return window.owner.currentWindow
  }
  val windows = window.owner.windows().toList()
  return if (windows.contains(window)) window else windows.firstOrNull()
}

private class SwitcherScrollPane(view: Component, noBorder: Boolean)
  : JBScrollPane(view, VERTICAL_SCROLLBAR_AS_NEEDED, if (noBorder) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER) {
  private var width = 0

  init {
    border = if (noBorder) JBUI.Borders.empty() else JBUI.Borders.customLineRight(JBUI.CurrentTheme.Popup.separatorColor())
    viewportBorder = JBUI.Borders.empty()
    minimumSize = JBUI.size(if (noBorder) 250 else 0, 100)
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    if (isPreferredSizeSet) return size
    val min = super.getMinimumSize()
    if (size.width < min.width) size.width = min.width
    if (size.height < min.height) size.height = min.height
    if (HORIZONTAL_SCROLLBAR_NEVER != getHorizontalScrollBarPolicy()) return size
    width = max(size.width, width)
    size.width = width
    return size
  }
}
