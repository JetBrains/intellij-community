// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.ui.JBListWithOpenInRightSplit;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.ide.util.gotoByName.QuickSearchComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.hover.ListHoverListener;
import com.intellij.ui.popup.PopupUpdateProcessorBase;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

import static com.intellij.codeInsight.hint.HintUtil.createAdComponent;
import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.RecentFiles;
import static com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode.*;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.util.registry.Registry.is;

/**
 * @author Konstantin Bulenkov
 */
public final class Switcher extends BaseSwitcherAction {
  public static final Key<SwitcherPanel> SWITCHER_KEY = Key.create("SWITCHER_KEY");

  public Switcher() {
    super(null);
  }

  /**
   * @deprecated Please use {@link Switcher#createAndShowSwitcher(AnActionEvent, String, boolean, boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Nullable
  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull @Nls String title, boolean pinned, final VirtualFile @Nullable [] vFiles) {
    Project project = e.getProject();
    if (project == null) return null;
    SwitcherPanel switcher = SWITCHER_KEY.get(project);
    if (switcher != null && Objects.equals(switcher.myTitle, title)) return null;
    InputEvent event = e.getInputEvent();
    return new SwitcherPanel(project, title, event, pinned ? vFiles != null : null, event == null || !event.isShiftDown());
  }

  public static class SwitcherPanel extends BorderLayoutPanel implements DataProvider, QuickSearchComponent, Disposable {
    static final int SWITCHER_ELEMENTS_LIMIT = 30;

    final JBPopup myPopup;
    final JBList<SwitcherListItem> toolWindows;
    final JBList<SwitcherVirtualFile> files;
    final JCheckBox cbShowOnlyEditedFiles;
    final JLabel pathLabel = createAdComponent(" ", JBUI.Borders.compound(
      JBUI.Borders.customLineTop(JBUI.CurrentTheme.Advertiser.borderColor()),
      JBUI.CurrentTheme.Advertiser.border()), SwingConstants.LEFT);
    final Project project;
    final boolean recent; // false - Switcher, true - Recent files / Recently changed files
    final boolean pinned; // false - auto closeable on modifier key release, true - default popup
    final SwitcherKeyReleaseListener onKeyRelease;
    final SwitcherSpeedSearch mySpeedSearch;
    final String myTitle;
    private JBPopup myHint;

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return this.project;
      }
      if (PlatformCoreDataKeys.SELECTED_ITEM.is(dataId)) {
        if (files.isSelectionEmpty()) return null;
        SwitcherVirtualFile item = ContainerUtil.getOnlyItem(files.getSelectedValuesList());
        return item == null ? null : item.getFile();
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        if (files.isSelectionEmpty()) return null;
        VirtualFile[] array = ContainerUtil.map2Array(files.getSelectedValuesList(), VirtualFile.class, SwitcherVirtualFile::getFile);
        return array.length > 0 ? array : null;
      }
      return null;
    }

    final ClickListener myClickListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (pinned && (e.isControlDown() || e.isMetaDown() || e.isShiftDown())) return false;
        final Object source = e.getSource();
        if (source instanceof JList) {
          JList<?> jList = (JList<?>)source;
          if (jList.getSelectedIndex() == -1 && jList.getAnchorSelectionIndex() != -1) {
            jList.setSelectedIndex(jList.getAnchorSelectionIndex());
          }
          if (jList.getSelectedIndex() != -1) {
            navigate(e);
          }
        }
        return true;
      }
    };

    SwitcherPanel(@NotNull Project project,
                  @NotNull @Nls String title,
                  @Nullable InputEvent event,
                  @Nullable Boolean onlyEditedFiles,
                  boolean forward) {
      this.project = project;
      recent = onlyEditedFiles != null;
      onKeyRelease = new SwitcherKeyReleaseListener(recent ? null : event, this::navigate);
      pinned = !onKeyRelease.isEnabled();
      boolean onlyEdited = Boolean.TRUE.equals(onlyEditedFiles);
      myTitle = title;
      mySpeedSearch = recent && is("ide.recent.files.speed.search") ? new SwitcherSpeedSearch(this) : null;
      cbShowOnlyEditedFiles = !recent || !Experiments.getInstance().isFeatureEnabled("recent.and.edited.files.together")
                                      ? null : new JCheckBox(IdeBundle.message("recent.files.checkbox.label"));

      SwitcherListRenderer renderer = new SwitcherListRenderer(this);
      List<SwitcherToolWindow> windows = renderer.getToolWindows();
      boolean showMnemonics = mySpeedSearch == null || is("ide.recent.files.tool.window.mnemonics");
      if (showMnemonics || is("ide.recent.files.tool.window.sort.by.mnemonics")) {
        updateMnemonics(windows, showMnemonics);
      }
      // register custom actions as soon as possible to block overridden actions
      registerAction(this::navigate, "ENTER");
      registerAction(this::hideSpeedSearchOrPopup, "ESCAPE");
      if (pinned) {
        registerAction(this::closeTabOrToolWindow, ActionUtil.getShortcutSet("DeleteRecentFiles"));
        registerAction(this::navigate, ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_NEW_WINDOW));
        registerAction(this::navigate, ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT));
      }
      else {
        registerAction(this::closeTabOrToolWindow, "DELETE", "BACK_SPACE");
        registerSwingAction(ListActions.Up.ID, "KP_UP", "UP");
        registerSwingAction(ListActions.Down.ID, "KP_DOWN", "DOWN");
        registerSwingAction(ListActions.Left.ID, "KP_LEFT", "LEFT");
        registerSwingAction(ListActions.Right.ID, "KP_RIGHT", "RIGHT");
        registerSwingAction(ListActions.PageUp.ID, "PAGE_UP");
        registerSwingAction(ListActions.PageDown.ID, "PAGE_DOWN");
      }
      if (mySpeedSearch == null || is("ide.recent.files.tool.window.mnemonics")) {
        windows.forEach(this::registerToolWindowAction);
      }

      setBorder(JBUI.Borders.empty());
      setBackground(JBColor.background());
      pathLabel.putClientProperty(SwingTextTrimmer.KEY, SwingTextTrimmer.THREE_DOTS_AT_LEFT);

      JPanel header = new JPanel(new HorizontalLayout(5));
      header.setBackground(JBUI.CurrentTheme.Popup.headerBackground(false));
      header.setBorder(JBUI.Borders.empty(4, 8));
      header.add(HorizontalLayout.LEFT, RelativeFont.BOLD.install(new JLabel(title)));

      if (cbShowOnlyEditedFiles != null) {
        cbShowOnlyEditedFiles.setOpaque(false);
        cbShowOnlyEditedFiles.setFocusable(false);
        cbShowOnlyEditedFiles.setSelected(onlyEdited);
        cbShowOnlyEditedFiles.addItemListener(this::updateFilesByCheckBox);
        header.add(HorizontalLayout.RIGHT, cbShowOnlyEditedFiles);

        WindowMoveListener moveListener = new WindowMoveListener(header);
        header.addMouseListener(moveListener);
        header.addMouseMotionListener(moveListener);

        ShortcutSet shortcuts = getActiveKeymapShortcuts("SwitcherRecentEditedChangedToggleCheckBox");
        if (shortcuts.getShortcuts().length > 0) {
          JLabel label = new JLabel(KeymapUtil.getShortcutsText(shortcuts.getShortcuts()));
          label.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
          header.add(HorizontalLayout.RIGHT, label);
        }
      }

      CollectionListModel<SwitcherListItem> twModel = new CollectionListModel<>();
      windows.stream().sorted((o1, o2) -> {
        String m1 = o1.getMnemonic();
        String m2 = o2.getMnemonic();
        return m1 == null
               ? (m2 == null ? 0 : 1)
               : (m2 == null ? -1 : m1.compareTo(m2));
      }).forEach(twModel::add);
      if (pinned && !windows.isEmpty()) {
        twModel.add(new SwitcherRecentLocations(this));
      }
      if (!showMnemonics) {
        windows.forEach(window -> window.setMnemonic(null));
      }

      toolWindows = new JBList<>(mySpeedSearch != null ? mySpeedSearch.wrap(twModel) : twModel);
      toolWindows.setVisibleRowCount(toolWindows.getItemsCount());
      toolWindows.setBorder(JBUI.Borders.empty(5, 0));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.getAccessibleContext().setAccessibleName(IdeBundle.message("recent.files.accessible.tool.window.list"));
      toolWindows.setEmptyText(IdeBundle.message("recent.files.tool.window.list.empty.text"));
      toolWindows.setCellRenderer(renderer);
      toolWindows.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
      toolWindows.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(toolWindows);
      ListHoverListener.DEFAULT.addTo(toolWindows);
      myClickListener.installOn(toolWindows);

      CollectionListModel<SwitcherVirtualFile> filesModel = new CollectionListModel<>();
      List<SwitcherVirtualFile> filesToShow = getFilesToShow(project, onlyEdited, toolWindows.getItemsCount(), recent);
      resetListModelAndUpdateNames(filesModel, filesToShow);

      final ListSelectionListener filesSelectionListener = new ListSelectionListener() {
        private @NlsSafe String getTitle2Text(@Nullable String fullText) {
          if (StringUtil.isEmpty(fullText)) return " ";
          return fullText;
        }

        @Override
        public void valueChanged(@NotNull final ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          updatePathLabel();
          PopupUpdateProcessorBase popupUpdater = myHint == null || !myHint.isVisible() ?
                                                  null : myHint.getUserData(PopupUpdateProcessorBase.class);
          if (popupUpdater != null) popupUpdater.updatePopup(CommonDataKeys.PSI_ELEMENT.getData(
            DataManager.getInstance().getDataContext(SwitcherPanel.this)));
        }

        private void updatePathLabel() {
          List<? extends SwitcherListItem> values = getSelectedList().getSelectedValuesList();
          if (values != null && values.size() == 1) {
            pathLabel.setText(getTitle2Text(values.get(0).getStatusText()));
          }
          else {
            pathLabel.setText(" ");
          }
        }
      };
      files = JBListWithOpenInRightSplit
        .createListWithOpenInRightSplitter(mySpeedSearch != null ? mySpeedSearch.wrap(filesModel) : filesModel, null, true);
      files.setVisibleRowCount(files.getItemsCount());
      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getAccessibleContext().setAccessibleName(IdeBundle.message("recent.files.accessible.file.list"));
      files.setEmptyText(IdeBundle.message("recent.files.file.list.empty.text"));

      toolWindows.getSelectionModel().addListSelectionListener(filesSelectionListener);
      files.getSelectionModel().addListSelectionListener(filesSelectionListener);

      files.setCellRenderer(renderer);
      files.setBorder(JBUI.Borders.empty(5, 0));
      files.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(files);
      ListHoverListener.DEFAULT.addTo(files);
      myClickListener.installOn(files);

      if (filesModel.getSize() > 0) {
        int selectionIndex = getFilesSelectedIndex(project, files, forward);
        files.setSelectedIndex(selectionIndex > -1 ? selectionIndex : 0);
      }
      else {
        ScrollingUtil.ensureSelectionExists(toolWindows);
      }
      addToTop(header);
      addToBottom(pathLabel);
      addToCenter(new SwitcherScrollPane(files, true));
      if (!windows.isEmpty()) {
        addToLeft(new SwitcherScrollPane(toolWindows, false));
      }

      if (mySpeedSearch != null) {
        // copy a speed search listener from the panel to the lists
        KeyListener listener = ArrayUtil.getLastElement(getKeyListeners());
        files.addKeyListener(listener);
        toolWindows.addKeyListener(listener);
      }

      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, !files.isEmpty() || toolWindows.isEmpty() ? files : toolWindows)
        .setResizable(pinned)
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setDimensionServiceKey(pinned ? project : null, pinned ? "SwitcherDM" : null , false)
        .setCancelKeyEnabled(false)
        .createPopup();
      Disposer.register(myPopup, this);

      if (pinned) {
        myPopup.setMinimumSize(new JBDimension(windows.isEmpty() ? 300 : 500, 200));
      }

      setFocusCycleRoot(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

      new SwitcherListFocusAction(files, toolWindows, ListActions.Left.ID);
      new SwitcherListFocusAction(toolWindows, files, ListActions.Right.ID);

      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);

      SwitcherPanel old = project.getUserData(SWITCHER_KEY);
      if (old != null) old.cancel();
      project.putUserData(SWITCHER_KEY, this);

      myPopup.showCenteredInCurrentWindow(project);
    }

    @Override
    public void dispose() {
      project.putUserData(SWITCHER_KEY, null);
    }

    boolean isOnlyEditedFilesShown() {
      return cbShowOnlyEditedFiles != null && cbShowOnlyEditedFiles.isSelected();
    }

    boolean isSpeedSearchPopupActive() {
      return mySpeedSearch != null && mySpeedSearch.isPopupActive();
    }

    @Override
    public void registerHint(@NotNull JBPopup h) {
      if (myHint != null && myHint.isVisible() && myHint != h) {
        myHint.cancel();
      }
      myHint = h;
    }

    @Override
    public void unregisterHint() {
      myHint = null;
    }

    private static @NotNull List<VirtualFile> collectFiles(@NotNull Project project, boolean onlyEdited) {
      return onlyEdited ? IdeDocumentHistory.getInstance(project).getChangedFiles() : getRecentFiles(project);
    }

    @NotNull
    private static List<SwitcherVirtualFile> getFilesToShow(@NotNull Project project, boolean onlyEdited, int toolWindowsCount, boolean pinned) {
      FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      List<SwitcherVirtualFile> filesData = new ArrayList<>();
      ArrayList<SwitcherVirtualFile> editors = new ArrayList<>();
      Set<VirtualFile> addedFiles = new LinkedHashSet<>();
      if (!pinned) {
        for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
          editors.add(new SwitcherVirtualFile(project, pair.first, pair.second));
        }
      }

      if (!pinned) {
        for (SwitcherVirtualFile editor : editors) {
          addedFiles.add(editor.getFile());
          filesData.add(editor);
          if (filesData.size() >= SWITCHER_ELEMENTS_LIMIT) break;
        }
      }

      if (filesData.size() <= 1) {
        List<? extends VirtualFile> filesForInit = collectFiles(project, onlyEdited);
        if (!filesForInit.isEmpty()) {
          int editorsFilesCount = (int)editors.stream().map(info -> info.getFile()).distinct().count();
          int maxFiles = Math.max(editorsFilesCount, filesForInit.size());
          int minIndex = pinned ? 0 : (filesForInit.size() - Math.min(toolWindowsCount, maxFiles));
          for (int i = filesForInit.size() - 1; i >= minIndex; i--) {

            SwitcherVirtualFile info = new SwitcherVirtualFile(project, filesForInit.get(i), null);
            boolean add = true;
            if (pinned) {
              for (SwitcherVirtualFile fileInfo : filesData) {
                if (fileInfo.getFile().equals(info.getFile())) {
                  add = false;
                  break;
                }
              }
            }
            if (add) {
              if (addedFiles.add(info.getFile())) {
                filesData.add(info);
              }
            }
          }
        }
        if (editors.size() == 1 && (filesData.isEmpty() || !editors.get(0).getFile().equals(filesData.get(0).getFile()))) {
          if (addedFiles.add(editors.get(0).getFile())) {
            filesData.add(0, editors.get(0));
          }
        }
      }
      return filesData;
    }

    static int getFilesSelectedIndex(@NotNull Project project, @NotNull JList<?> filesList, boolean forward) {
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      EditorWindow currentWindow = editorManager.getCurrentWindow();
      VirtualFile currentFile = currentWindow != null ? currentWindow.getSelectedFile() : null;

      ListModel<?> model = filesList.getModel();
      if (forward) {
        for (int i = 0; i < model.getSize(); i++) {
          if (!isTheSameTab(currentWindow, currentFile, model.getElementAt(i))) {
            return i;
          }
        }
      }
      else {
        for (int i = model.getSize() - 1; i >= 0; i--) {
          if (!isTheSameTab(currentWindow, currentFile, model.getElementAt(i))) {
            return i;
          }
        }
      }

      return -1;
    }

    private static boolean isTheSameTab(EditorWindow currentWindow, VirtualFile currentFile, Object element) {
      SwitcherVirtualFile svf = element instanceof SwitcherVirtualFile ? (SwitcherVirtualFile)element : null;
      return svf != null && svf.getFile().equals(currentFile) && (svf.getWindow() == null || svf.getWindow().equals(currentWindow));
    }

    @NotNull
    private static List<VirtualFile> getRecentFiles(@NotNull Project project) {
      List<VirtualFile> recentFiles = EditorHistoryManager.getInstance(project).getFileList();
      VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();

      Set<VirtualFile> recentFilesSet = new HashSet<>(recentFiles);
      Set<VirtualFile> openFilesSet = ContainerUtil.newHashSet(openFiles);

      // Add missing FileEditor tabs right after the last one, that is available via "Recent Files"
      int index = 0;
      for (int i = 0; i < recentFiles.size(); i++) {
        if (openFilesSet.contains(recentFiles.get(i))) {
          index = i;
          break;
        }
      }

      List<VirtualFile> result = new ArrayList<>(recentFiles);
      result.addAll(index, ContainerUtil.filter(openFiles, it -> !recentFilesSet.contains(it)));
      return result;
    }

    private void updateMnemonics(@NotNull List<SwitcherToolWindow> windows, boolean showMnemonics) {
      final Map<String, SwitcherToolWindow> keymap = new HashMap<>(windows.size());
      keymap.put(onKeyRelease.getForbiddenMnemonic(), null);
      addForbiddenMnemonics(keymap, "SwitcherForward");
      addForbiddenMnemonics(keymap, "SwitcherBackward");
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
      addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
      final List<SwitcherToolWindow> otherTW = new ArrayList<>();
      for (SwitcherToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(window.getWindow().getId());
        if (index < '0' || index > '9' || !addShortcut(keymap, window, getIndexShortcut(index - '0'))) {
          otherTW.add(window);
        }
      }
      if (!showMnemonics && !is("ide.recent.files.tool.window.sort.by.automatic.mnemonics")) return;
      int i = 0;
      for (SwitcherToolWindow window : otherTW) {
        if (addSmartShortcut(window, keymap)) {
          continue;
        }

        while (!addShortcut(keymap, window, getIndexShortcut(i))) {
          i++;
        }
        i++;
      }
    }

    private void addForbiddenMnemonics(@NotNull Map<String, SwitcherToolWindow> keymap, @NotNull String actionId) {
      for (Shortcut shortcut : ActionUtil.getShortcutSet(actionId).getShortcuts()) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          keymap.put(onKeyRelease.getForbiddenMnemonic(keyboardShortcut.getFirstKeyStroke()), null);
        }
      }
    }

    private static boolean addShortcut(Map<String, SwitcherToolWindow> keymap, SwitcherToolWindow window, String shortcut) {
      if (keymap.containsKey(shortcut)) return false;
      keymap.put(shortcut, window);
      window.setMnemonic(shortcut);
      return true;
    }

    private static boolean addSmartShortcut(SwitcherToolWindow window, Map<String, SwitcherToolWindow> keymap) {
      String title = window.getMainText();
      if (StringUtil.isEmpty(title))
        return false;
      for (int i = 0; i < title.length(); i++) {
        char c = title.charAt(i);
        if (Character.isUpperCase(c) && addShortcut(keymap, window, String.valueOf(c))) {
          return true;
        }
      }
      return false;
    }

    private static String getIndexShortcut(int index) {
      return StringUtil.toUpperCase(Integer.toString(index, index + 1));
    }

    private void closeTabOrToolWindow(@Nullable InputEvent event) {
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
        mySpeedSearch.updateEnteredPrefix();
        return;
      }
      JList<? extends SwitcherListItem> selectedList = getSelectedList();
      final int[] selected = selectedList.getSelectedIndices();
      Arrays.sort(selected);
      int selectedIndex = 0;
      for (int i = selected.length - 1; i >= 0; i--) {
        selectedIndex = selected[i];
        SwitcherListItem item = selectedList.getModel().getElementAt(selectedIndex);
        if (item instanceof SwitcherVirtualFile) {
          SwitcherVirtualFile svf = (SwitcherVirtualFile)item;
          VirtualFile virtualFile = svf.getFile();
          final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          EditorWindow wnd = findAppropriateWindow(svf.getWindow());
          if (wnd == null) {
            editorManager.closeFile(virtualFile, false, false);
          }
          else {
            editorManager.closeFile(virtualFile, wnd, false);
          }
          ListUtil.removeItem(files.getModel(), selectedIndex);
          if (svf.getWindow() == null) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile);
          }
        }
        else if (item != null) {
          item.close(this);
        }
      }
      if (files == selectedList) {
        int size = files.getItemsCount();
        if (size > 0) {
          int index = Math.min(Math.max(selectedIndex, 0), size - 1);
          files.setSelectedIndex(index);
          files.ensureIndexIsVisible(index);
        }
        else {
          toolWindows.requestFocusInWindow();
        }
      }
    }

    private void cancel() {
      myPopup.cancel();
    }

    private void hideSpeedSearchOrPopup(@Nullable InputEvent event) {
      if (mySpeedSearch == null || !mySpeedSearch.isPopupActive()) {
        cancel();
      }
      else {
        mySpeedSearch.hidePopup();
      }
    }

    public void go(boolean forward) {
      JBList<? extends SwitcherListItem> selected = getSelectedList();
      JBList<? extends SwitcherListItem> list = selected;
      int index = list.getSelectedIndex();
      if (forward) index++; else index--;
      if ((forward && index >= list.getItemsCount()) || (!forward && index < 0)) {
        if (!toolWindows.isEmpty() && !files.isEmpty()) {
          list = list == files ? toolWindows : files;
        }
        index = forward ? 0 : list.getItemsCount() - 1;
      }
      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
      if (selected != list) {
        IdeFocusManager.findInstanceByComponent(list).requestFocus(list, true);
      }
    }

    public void goForward() {
      go(true);
    }

    public void goBack() {
      go(false);
    }

    public JBList<? extends SwitcherListItem> getSelectedList() {
      return getSelectedList(files);
    }

    @Nullable
    JBList<? extends SwitcherListItem> getSelectedList(@Nullable JBList<? extends SwitcherListItem> preferable) {
      return files.hasFocus() ? files : toolWindows.hasFocus() ? toolWindows : preferable;
    }

    private void updateFilesByCheckBox(@NotNull ItemEvent event) {
      boolean onlyEdited = ItemEvent.SELECTED == event.getStateChange();
      final boolean listWasSelected = files.getSelectedIndex() != -1;

      List<SwitcherVirtualFile> filesToShow = getFilesToShow(project, onlyEdited, toolWindows.getItemsCount(), recent);
      resetListModelAndUpdateNames((CollectionListModel<SwitcherVirtualFile>)((FilteringListModel<SwitcherVirtualFile>)files.getModel()).getOriginalModel(), filesToShow);

      int selectionIndex = getFilesSelectedIndex(project, files, true);
      if (selectionIndex > -1 && listWasSelected) {
        files.setSelectedIndex(selectionIndex);
      }
      files.revalidate();
      files.repaint();
      // refresh the Recent Locations item
      ListModel<SwitcherListItem> toolWindowsModel = toolWindows.getModel();
      if (toolWindowsModel instanceof NameFilteringListModel) {
        ((NameFilteringListModel<?>)toolWindowsModel).refilter();
      }
      toolWindows.repaint();
    }

    private void resetListModelAndUpdateNames(CollectionListModel<SwitcherVirtualFile> model, List<SwitcherVirtualFile> items) {
      for (SwitcherVirtualFile datum : items) {
        datum.setMainText(datum.getFile().getPresentableName());
      }
      model.removeAll();
      model.addAll(0, items);
      ReadAction.nonBlocking(() -> ContainerUtil.map2Map(
          items, o -> Pair.create(o.getFile(), VfsPresentationUtil.getUniquePresentableNameForUI(o.getProject(), o.getFile())))
        )
        .expireWith(this)
        .finishOnUiThread(ModalityState.any(), map -> {
          for (SwitcherVirtualFile item : items) {
            item.setMainText(map.get(item.getFile()));
          }
          files.invalidate();
          files.repaint();
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }

    void navigate(final InputEvent e) {
      FileEditorManagerImpl.OpenMode mode = e != null ? FileEditorManagerImpl.getOpenMode(e) : DEFAULT;
      List<?> values = getSelectedList().getSelectedValuesList();
      String searchQuery = mySpeedSearch != null ? mySpeedSearch.getEnteredPrefix() : null;
      cancel();
      if (values.isEmpty()) {
        tryToOpenFileSearch(e, searchQuery);
      }
      else if (values.get(0) instanceof SwitcherVirtualFile) {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          EditorWindow splitWindow = null;
          for (Object value : values) {
            if (value instanceof SwitcherVirtualFile) {
              SwitcherVirtualFile item = (SwitcherVirtualFile)value;

              VirtualFile file = item.getFile();
              if (mode == RIGHT_SPLIT) {
                if (splitWindow == null) {
                  splitWindow = OpenInRightSplitAction.Companion.openInRightSplit(project, file, null, true);
                }
                else {
                  manager.openFileWithProviders(file, true, splitWindow);
                }
              }
              if (mode == NEW_WINDOW) {
                manager.openFileInNewWindow(file);
              }
              else if (item.getWindow() != null) {
                EditorWindow wnd = findAppropriateWindow(item.getWindow());
                if (wnd != null) {
                  manager.openFileImpl2(wnd, file, true);
                  manager.addSelectionRecord(file, wnd);
                }
              }
              else {
                UISettingsState settings = UISettings.getInstance().getState();
                boolean oldValue = settings.getReuseNotModifiedTabs();
                settings.setReuseNotModifiedTabs(false);
                manager.openFile(file, true, true);
                if (LightEdit.owns(project)) {
                  LightEditFeatureUsagesUtil.logFileOpen(project, RecentFiles);
                }
                if (oldValue) {
                  CommandProcessor.getInstance().executeCommand(project, () -> settings.setReuseNotModifiedTabs(true), "", null);
                }
              }
            }
          }
        }, ModalityState.current());
      }
      else if (values.get(0) instanceof SwitcherListItem) {
        SwitcherListItem item = (SwitcherListItem)values.get(0);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> item.navigate(this, mode), ModalityState.current());
      }
    }

    private void tryToOpenFileSearch(final InputEvent e, final String fileName) {
      AnAction gotoFile = ActionManager.getInstance().getAction("GotoFile");
      if (gotoFile != null && !StringUtil.isEmpty(fileName)) {
        cancel();
        final AnAction action = gotoFile;
        ApplicationManager.getApplication().invokeLater(() -> DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)context -> {
          final DataContext dataContext = dataId -> {
            if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
              return fileName;
            }
            return context.getData(dataId);
          };
          final AnActionEvent event =
            new AnActionEvent(e, dataContext, ActionPlaces.EDITOR_POPUP, new PresentationFactory().getPresentation(action),
                              ActionManager.getInstance(), 0);
          action.actionPerformed(event);
        }), ModalityState.current());
      }
    }

    private void registerAction(@NotNull Consumer<InputEvent> action, @NonNls String @NotNull ... keys) {
      registerAction(action, onKeyRelease.getShortcuts(keys));
    }

    private void registerAction(@NotNull Consumer<InputEvent> action, @NotNull ShortcutSet shortcuts) {
      if (shortcuts.getShortcuts().length == 0) return; // ignore empty shortcut set
      LightEditActionFactory.create(event -> {
        if (myPopup != null && myPopup.isVisible()) action.consume(event.getInputEvent());
      }).registerCustomShortcutSet(shortcuts, this, this);
    }

    private void registerSwingAction(@NonNls @NotNull String id, @NonNls String @NotNull ... keys) {
      registerAction(event -> SwingActionDelegate.performAction(id, getSelectedList(null)), keys);
    }

    private void registerToolWindowAction(@NotNull SwitcherToolWindow window) {
      String mnemonic = window.getMnemonic();
      if (!StringUtil.isEmpty(mnemonic)) {
        registerAction(event -> {
          cancel();
          window.getWindow().activate(null, true, true);
        }, mySpeedSearch == null
           ? onKeyRelease.getShortcuts(mnemonic)
           : SystemInfo.isMac
             ? CustomShortcutSet.fromString("alt " + mnemonic, "alt control " + mnemonic)
             : CustomShortcutSet.fromString("alt " + mnemonic));
      }
    }

    private static @Nullable EditorWindow findAppropriateWindow(@Nullable EditorWindow window) {
      if (window == null) return null;
      if (UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE) {
        return window.getOwner().getCurrentWindow();
      }
      final EditorWindow[] windows = window.getOwner().getWindows();
      return ArrayUtil.contains(window, windows) ? window : windows.length > 0 ? windows[0] : null;
    }

    @TestOnly
    static List<VirtualFile> getFilesToShowForTest(@NotNull Project project) {
      return ContainerUtil.map2List(getFilesToShow(project, false, 10, true), SwitcherVirtualFile::getFile);
    }

    @TestOnly
    static int getFilesSelectedIndexForTest(@NotNull Project project, boolean goForward) {
      return getFilesSelectedIndex(project, new JBList<>(getFilesToShow(project, false, 10, true)), goForward);
    }
  }

  private static final class SwitcherScrollPane extends JBScrollPane {
    private int width;

    SwitcherScrollPane(@NotNull Component view, boolean noBorder) {
      super(view, VERTICAL_SCROLLBAR_AS_NEEDED, noBorder ? HORIZONTAL_SCROLLBAR_AS_NEEDED : HORIZONTAL_SCROLLBAR_NEVER);
      setBorder(noBorder ? JBUI.Borders.empty() : JBUI.Borders.customLineRight(JBUI.CurrentTheme.Popup.separatorColor()));
      setViewportBorder(JBUI.Borders.empty());
      setMinimumSize(JBUI.size(noBorder ? 250 : 0, 100));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (isPreferredSizeSet()) return size;
      Dimension min = super.getMinimumSize();
      if (size.width < min.width) size.width = min.width;
      if (size.height < min.height) size.height = min.height;
      if (HORIZONTAL_SCROLLBAR_NEVER != getHorizontalScrollBarPolicy()) return size;
      size.width = width = Math.max(size.width, width);
      return size;
    }
  }
}
