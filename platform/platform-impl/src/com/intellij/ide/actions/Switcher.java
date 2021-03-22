// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.hover.ListHoverListener;
import com.intellij.ui.popup.PopupUpdateProcessorBase;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.*;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.RecentFiles;
import static com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode.*;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.util.registry.Registry.is;

/**
 * @author Konstantin Bulenkov
 */
public final class Switcher extends BaseSwitcherAction {
  public static final Key<SwitcherPanel> SWITCHER_KEY = Key.create("SWITCHER_KEY");

  private static final int MINIMUM_HEIGHT = JBUIScale.scale(400);
  private static final int MINIMUM_WIDTH = JBUIScale.scale(500);

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
    final JLabel pathLabel = new JLabel(" ");
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
      if (PlatformDataKeys.SELECTED_ITEM.is(dataId)) {
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
          JList<? extends SwitcherListItem> jList = (JList<? extends SwitcherListItem>)source;
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
      updateMnemonics(windows);
      // register custom actions as soon as possible to block overridden actions
      registerAction(this::navigate, "ENTER");
      registerAction(this::hideSpeedSearchOrPopup, "ESCAPE");
      registerAction(this::closeTabOrToolWindow, "DELETE", "BACK_SPACE");
      if (!pinned) {
        registerSwingAction(ListActions.Up.ID, "KP_UP", "UP");
        registerSwingAction(ListActions.Down.ID, "KP_DOWN", "DOWN");
        registerSwingAction(ListActions.Left.ID, "KP_LEFT", "LEFT");
        registerSwingAction(ListActions.Right.ID, "KP_RIGHT", "RIGHT");
        registerSwingAction(ListActions.PageUp.ID, "PAGE_UP");
        registerSwingAction(ListActions.PageDown.ID, "PAGE_DOWN");
      }
      if (mySpeedSearch == null) {
        windows.forEach(this::registerToolWindowAction);
      }

      setBorder(JBUI.Borders.empty());
      setBackground(JBColor.background());
      pathLabel.setHorizontalAlignment(SwingConstants.LEFT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont(Math.max(10f, font.getSize() - 4f)));
      pathLabel.setBorder(JBUI.CurrentTheme.Advertiser.border());
      pathLabel.setForeground(JBUI.CurrentTheme.Advertiser.foreground());
      pathLabel.setBackground(JBUI.CurrentTheme.Advertiser.background());
      pathLabel.setOpaque(true);

      BorderLayoutPanel footer = new BorderLayoutPanel();
      footer.setBackground(JBUI.CurrentTheme.Advertiser.background());
      footer.setBorder(new CustomLineBorder(JBUI.CurrentTheme.Advertiser.borderColor(), JBUI.insetsTop(1)));
      footer.addToCenter(pathLabel);

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
      if (pinned) {
        twModel.add(new SwitcherRecentLocations(this));
      }
      if (mySpeedSearch != null) {
        windows.forEach(window -> window.setMnemonic(null));
      }

      toolWindows = new JBList<>(createModel(twModel, SwitcherListItem::getMainText, mySpeedSearch));
      toolWindows.setVisibleRowCount(toolWindows.getModel().getSize());
      toolWindows.setBorder(JBUI.Borders.empty(5, 0));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.getAccessibleContext().setAccessibleName(IdeBundle.message("recent.files.accessible.tool.window.list"));
      toolWindows.setCellRenderer(renderer);
      toolWindows.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
      toolWindows.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(toolWindows);
      ListHoverListener.DEFAULT.addTo(toolWindows);
      ScrollingUtil.ensureSelectionExists(toolWindows);
      myClickListener.installOn(toolWindows);

      final List<FileInfo> filesToShow = getFilesToShow(project, collectFiles(project, onlyEdited),
                                                        toolWindows.getModel().getSize(), recent);
      CollectionListModel<SwitcherVirtualFile> filesModel = new CollectionListModel<>(wrap(filesToShow));

      final ListSelectionListener filesSelectionListener = new ListSelectionListener() {
        private @NlsSafe String getTitle2Text(@Nullable String fullText) {
          int labelWidth = pathLabel.getWidth();
          if (fullText == null || fullText.length() == 0) return " ";
          while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
            int sep = fullText.indexOf(File.separatorChar, 4);
            if (sep < 0) return fullText;
            fullText = "..." + fullText.substring(sep);
          }

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
        .createListWithOpenInRightSplitter(createModel(filesModel, SwitcherListItem::getMainText, mySpeedSearch), null, true);
      files.setVisibleRowCount(toolWindows.getModel().getSize());
      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getAccessibleContext().setAccessibleName(IdeBundle.message("recent.files.accessible.file.list"));

      toolWindows.getSelectionModel().addListSelectionListener(filesSelectionListener);
      files.getSelectionModel().addListSelectionListener(filesSelectionListener);

      files.setCellRenderer(renderer);
      files.setBorder(JBUI.Borders.empty(5, 0));
      files.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(files);
      ListHoverListener.DEFAULT.addTo(files);
      myClickListener.installOn(files);
      ScrollingUtil.ensureSelectionExists(files);

      if (filesModel.getSize() > 0) {
        addToCenter(new SwitcherScrollPane(files, JBUI.CurrentTheme.Popup.separatorColor()));
        int selectionIndex = getFilesSelectedIndex(project, files, forward);
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
      }
      addToTop(header);
      addToLeft(new SwitcherScrollPane(toolWindows, null));
      addToBottom(footer);

      if (mySpeedSearch != null) {
        // copy a speed search listener from the panel to the lists
        KeyListener listener = ArrayUtil.getLastElement(getKeyListeners());
        files.addKeyListener(listener);
        toolWindows.addKeyListener(listener);
      }

      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, filesModel.getSize() > 0 ? files : toolWindows)
        .setResizable(pinned)
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setMinSize(new Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT))
        .setDimensionServiceKey(pinned ? project : null, pinned ? "SwitcherDM" : null , false)
        .setCancelKeyEnabled(false)
        .createPopup();
      Disposer.register(myPopup, this);

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

    private static <T> ListModel<T> createModel(CollectionListModel<T> baseModel,
                                                Function<? super T, String> namer,
                                                SpeedSearchBase<SwitcherPanel> speedSearch) {
      ListModel<T> listModel;
      if (speedSearch != null) {
        listModel = new NameFilteringListModel<>(baseModel, namer, s -> !speedSearch.isPopupActive()
                                                                        || StringUtil.isEmpty(speedSearch.getEnteredPrefix())
                                                                        || speedSearch.getComparator().matchingFragments(speedSearch.getEnteredPrefix(), s) != null, () -> StringUtil.notNullize(
          speedSearch.getEnteredPrefix()));
      }
      else {
        listModel = baseModel;
      }
      return listModel;
    }

    static @NotNull List<VirtualFile> collectFiles(@NotNull Project project, boolean onlyEdited) {
      return onlyEdited ? IdeDocumentHistory.getInstance(project).getChangedFiles() : getRecentFiles(project);
    }

    static @NotNull List<SwitcherVirtualFile> wrap(@NotNull List<FileInfo> list) {
      return ContainerUtil.map(list, info -> {
        SwitcherVirtualFile svf = new SwitcherVirtualFile(info.myProject, info.first, info.second);
        svf.setMainText(info.getNameForRendering());
        return svf;
      });
    }

    @NotNull
    static List<FileInfo> getFilesToShow(@NotNull Project project, @NotNull List<? extends VirtualFile> filesForInit,
                                         int toolWindowsCount, boolean pinned) {
      FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      List<FileInfo> filesData = new ArrayList<>();
      ArrayList<FileInfo> editors = new ArrayList<>();
      Set<VirtualFile> addedFiles = new LinkedHashSet<>();
      if (!pinned) {
        for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
          editors.add(new FileInfo(pair.first, pair.second, project));
        }
      }

      if (!pinned) {
        for (FileInfo editor : editors) {
          addedFiles.add(editor.first);
          filesData.add(editor);
          if (filesData.size() >= SWITCHER_ELEMENTS_LIMIT) break;
        }
      }

      List<VirtualFile> selectedFiles = Arrays.asList(editorManager.getSelectedFiles());
      if (filesData.size() <= 1) {
        if (!filesForInit.isEmpty()) {
          int editorsFilesCount = (int) editors.stream().map(info -> info.first).distinct().count();
          int maxFiles = Math.max(editorsFilesCount, filesForInit.size());
          int minIndex = pinned ? 0 : (filesForInit.size() - Math.min(toolWindowsCount, maxFiles));
          for (int i = filesForInit.size() - 1; i >= minIndex; i--) {
            if (pinned
                && UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE
                && selectedFiles.contains(filesForInit.get(i))) {
              continue;
            }

            FileInfo info = new FileInfo(filesForInit.get(i), null, project);
            boolean add = true;
            if (pinned) {
              for (FileInfo fileInfo : filesData) {
                if (fileInfo.first.equals(info.first)) {
                  add = false;
                  break;
                }
              }
            }
            if (add) {
              if (addedFiles.add(info.first)) {
                filesData.add(info);
              }
            }
          }
        }
        if (editors.size() == 1 && (filesData.isEmpty() || !editors.get(0).getFirst().equals(filesData.get(0).getFirst()))) {
          if (addedFiles.add(editors.get(0).first)) {
            filesData.add(0, editors.get(0));
          }
        }
      }

      return filesData;
    }

    static int getFilesSelectedIndex(Project project, JList<?> filesList, boolean forward) {
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
      if (element instanceof FileInfo) return isTheSameTab(currentWindow, currentFile, (FileInfo)element);
      SwitcherVirtualFile svf = element instanceof SwitcherVirtualFile ? (SwitcherVirtualFile)element : null;
      return svf != null && svf.getFile().equals(currentFile) && (svf.getWindow() == null || svf.getWindow().equals(currentWindow));
    }

    private static boolean isTheSameTab(EditorWindow currentWindow, VirtualFile currentFile, FileInfo fileInfo) {
      return fileInfo.first.equals(currentFile) && (fileInfo.second == null || fileInfo.second.equals(currentWindow));
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

    private void updateMnemonics(@NotNull List<SwitcherToolWindow> windows) {
      final Map<String, SwitcherToolWindow> keymap = new HashMap<>(windows.size());
      keymap.put(onKeyRelease.getForbiddenMnemonic(), null);
      addForbiddenMnemonics(keymap, "SwitcherForward");
      addForbiddenMnemonics(keymap, "SwitcherBackward");
      final List<SwitcherToolWindow> otherTW = new ArrayList<>();
      for (SwitcherToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(window.getWindow().getId());
        if (index < '0' || index > '9' || !addShortcut(keymap, window, getIndexShortcut(index - '0'))) {
          otherTW.add(window);
        }
      }
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

    private static void addForbiddenMnemonics(@NotNull Map<String, SwitcherToolWindow> keymap, @NotNull String actionId) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action == null) return;
      for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          int code = keyboardShortcut.getFirstKeyStroke().getKeyCode();
          if ('0' <= code && code <= '9' || 'A' <= code && code <= 'Z') {
            keymap.put(String.valueOf((char)code), null);
          }
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
        JTextField field = mySpeedSearch.getSearchField();
        if (field != null) {
          String text = field.getText();
          int length = text == null ? 0 : text.length() - 1;
          boolean empty = length <= 0;
          field.setText(empty ? "" : text.substring(0, length));
          if (empty) mySpeedSearch.hidePopup();
        }
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
          if (recent) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile);
          }
        }
        else if (item != null) {
          item.close(this);
        }
      }
      int size = files.getModel().getSize();
      if (size > 0) {
        int index = Math.min(Math.max(selectedIndex, 0), size - 1);
        files.setSelectedIndex(index);
        files.ensureIndexIsVisible(index);
      }
      else {
        toolWindows.requestFocusInWindow();
      }
    }

    private boolean isFilesSelected() {
      return getSelectedList() == files;
    }

    private boolean isFilesVisible() {
      return files.getModel().getSize() > 0;
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
        if (mySpeedSearch.getElementCount() > 0) {
          mySpeedSearch.selectElement(mySpeedSearch.getElementAt(0), "");
        }
      }
    }

    public void go(boolean forward) {
      JList<? extends SwitcherListItem> selected = getSelectedList();
      JList<? extends SwitcherListItem> list = selected;
      int index = list.getSelectedIndex();
      if (forward) index++; else index--;
      if ((forward && index >= list.getModel().getSize()) || (!forward && index < 0)) {
        if (isFilesVisible()) {
          list = isFilesSelected() ? toolWindows : files;
        }
        index = forward ? 0 : list.getModel().getSize() - 1;
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

      final List<FileInfo> filesToShow = getFilesToShow(project, collectFiles(project, onlyEdited),
                                                        toolWindows.getModel().getSize(), recent);

      ListModel<SwitcherVirtualFile> model = files.getModel();
      ListUtil.removeAllItems(model);
      ListUtil.addAllItems(model, wrap(filesToShow));

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
                manager.openFile(file, true, UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE);
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
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          if (myPopup != null && myPopup.isVisible()) action.consume(event.getInputEvent());
        }
      }.registerCustomShortcutSet(onKeyRelease.getShortcuts(keys), this, this);
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
        }, mnemonic);
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

    private static class SwitcherSpeedSearch extends SpeedSearchBase<SwitcherPanel> implements PropertyChangeListener {

      SwitcherSpeedSearch(@NotNull SwitcherPanel switcher) {
        super(switcher);
        addChangeListener(this);
        setComparator(new SpeedSearchComparator(false, true));
      }

      @Override
      protected int getSelectedIndex() {
        return myComponent.isFilesSelected()
               ? myComponent.files.getSelectedIndex()
               : myComponent.files.getModel().getSize() + myComponent.toolWindows.getSelectedIndex();
      }

      @Override
      protected int getElementCount() {
        return myComponent.files.getModel().getSize() + myComponent.toolWindows.getModel().getSize();
      }

      @Override
      protected Object getElementAt(int index) {
        ListModel<?> files = myComponent.files.getModel();
        if (index < files.getSize()) return files.getElementAt(index);
        return myComponent.toolWindows.getModel().getElementAt(index - files.getSize());
      }

      @Override
      protected @NotNull String getElementText(Object element) {
        SwitcherListItem item = element instanceof SwitcherListItem ? (SwitcherListItem)element : null;
        return item == null ? "" : item.getMainText();
      }

      @Override
      protected void selectElement(final Object element, String selectedText) {
        if (element instanceof SwitcherVirtualFile) {
          if (!myComponent.toolWindows.isSelectionEmpty()) myComponent.toolWindows.clearSelection();
          myComponent.files.clearSelection();
          myComponent.files.setSelectedValue(element, true);
          myComponent.files.requestFocusInWindow();
        }
        else {
          if (!myComponent.files.isSelectionEmpty()) myComponent.files.clearSelection();
          myComponent.toolWindows.clearSelection();
          myComponent.toolWindows.setSelectedValue(element, true);
          myComponent.toolWindows.requestFocusInWindow();
        }
      }

      @Nullable
      @Override
      protected Object findElement(@NotNull String pattern) {
        boolean toolWindowsFocused = myComponent.toolWindows.hasFocus();
        JList<?> firstList = !toolWindowsFocused ? myComponent.files : myComponent.toolWindows;
        JList<?> secondList = toolWindowsFocused ? myComponent.files : myComponent.toolWindows;
        Object element = findElementIn(firstList, pattern);
        return element != null ? element : findElementIn(secondList, pattern);
      }

      private <T> @Nullable T findElementIn(@NotNull JList<T> list, @NotNull String pattern) {
        T foundElement = null;
        int foundDegree = 0;
        ListModel<T> model = list.getModel();
        for (int i = 0; i < model.getSize(); i++) {
          T element = model.getElementAt(i);
          String text = getElementText(element);
          if (text.isEmpty()) continue;
          int degree = getComparator().matchingDegree(pattern, text);
          if (foundElement == null || foundDegree < degree) {
            foundElement = element;
            foundDegree = degree;
          }
        }
        return foundElement;
      }

      @Override
      public void propertyChange(@NotNull PropertyChangeEvent evt) {
        if (myComponent.project.isDisposed()) {
          myComponent.cancel();
          return;
        }
        ((NameFilteringListModel)myComponent.files.getModel()).refilter();
        ((NameFilteringListModel)myComponent.toolWindows.getModel()).refilter();
        if (myComponent.files.getModel().getSize() + myComponent.toolWindows.getModel().getSize() == 0) {
          myComponent.toolWindows.getEmptyText().setText("");
          myComponent.files.getEmptyText().setText(IdeBundle.message("empty.text.press.enter.to.search.in.project"));
        }
        else {
          myComponent.files.getEmptyText().setText(StatusText.getDefaultEmptyText());
          myComponent.toolWindows.getEmptyText().setText(StatusText.getDefaultEmptyText());
        }
        refreshSelection();
      }
    }
  }

  static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    final Project myProject;
    private String myNameForRendering;

    FileInfo(VirtualFile first, EditorWindow second, Project project) {
      super(first, second);
      myProject = project;
    }

    @NlsSafe String getNameForRendering() {
      if (myNameForRendering == null) {
        // Recently changed files would also be taken into account (not only open 'visible' files)
        myNameForRendering = SlowOperations.allowSlowOperations(
          () -> EditorTabPresentationUtil.getUniqueEditorTabTitle(myProject, first, second)
        );
      }
      return myNameForRendering;
    }
  }


  private static final class SwitcherScrollPane extends JBScrollPane {
    private int width;

    SwitcherScrollPane(@NotNull Component view, @Nullable Color color) {
      super(view, VERTICAL_SCROLLBAR_AS_NEEDED, color == null ? HORIZONTAL_SCROLLBAR_NEVER : HORIZONTAL_SCROLLBAR_AS_NEEDED);
      setBorder(color == null ? JBUI.Borders.empty() : JBUI.Borders.customLineLeft(color));
      setViewportBorder(JBUI.Borders.empty());
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (isPreferredSizeSet()) return size;
      if (HORIZONTAL_SCROLLBAR_NEVER != getHorizontalScrollBarPolicy()) return size;
      size.width = width = Math.max(size.width, width);
      return size;
    }
  }
}
