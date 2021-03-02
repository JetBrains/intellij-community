// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
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
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.hover.ListHoverListener;
import com.intellij.ui.popup.PopupUpdateProcessorBase;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.RecentFiles;
import static com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.OpenMode.*;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static java.awt.event.KeyEvent.*;

/**
 * @author Konstantin Bulenkov
 */
public final class Switcher extends DumbAwareAction {
  public static final Key<SwitcherPanel> SWITCHER_KEY = Key.create("SWITCHER_KEY");
  private static final Color SEPARATOR_COLOR = JBColor.namedColor("Popup.separatorColor", new JBColor(Gray.xC0, Gray.x4B));

  private static final int MINIMUM_HEIGHT = JBUIScale.scale(400);
  private static final int MINIMUM_WIDTH = JBUIScale.scale(500);

  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    SwitcherPanel switcher = SWITCHER_KEY.get(project);
    if (switcher != null && !switcher.pinned) {
      switcher.go(e.getInputEvent());
    }
    else {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
      new SwitcherPanel(project, IdeBundle.message("window.title.switcher"), null, e.getInputEvent());
    }
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
    return new SwitcherPanel(project, title, pinned ? vFiles != null : null, e.getInputEvent());
  }

  public static class SwitcherPanel extends BorderLayoutPanel implements DataProvider, QuickSearchComponent, Disposable {
    static final int SWITCHER_ELEMENTS_LIMIT = 30;

    final JBPopup myPopup;
    final JBList<Object> toolWindows;
    final JBList<FileInfo> files;
    final JCheckBox cbShowOnlyEditedFiles;
    final JLabel pathLabel = new JLabel(" ");
    final Project project;
    final boolean pinned;
    final boolean wasAltDown;
    final boolean wasControlDown;
    final Map<String, SwitcherToolWindow> twShortcuts;
    final Alarm myAlarm;
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
        List list = getSelectedList().getSelectedValuesList();
        Object o = ContainerUtil.getOnlyItem(list);
        return o instanceof FileInfo ? ((FileInfo)o).first : null;
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        final List list = getSelectedList().getSelectedValuesList();
        if (!list.isEmpty()) {
          final List<VirtualFile> vFiles = new ArrayList<>();
          for (Object o : list) {
            if (o instanceof FileInfo) {
              vFiles.add(((FileInfo)o).first);
            }
          }
          return vFiles.isEmpty() ? null : vFiles.toArray(VirtualFile.EMPTY_ARRAY);
        }
      }
      return null;
    }

    final ClickListener myClickListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (pinned && (e.isControlDown() || e.isMetaDown() || e.isShiftDown())) return false;
        final Object source = e.getSource();
        if (source instanceof JList) {
          JList jList = (JList)source;
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

    SwitcherPanel(@NotNull Project project, @NotNull @Nls String title, @Nullable Boolean onlyEditedFiles, @Nullable InputEvent event) {
      this.project = project;
      wasAltDown = onlyEditedFiles == null && event != null && event.isAltDown();
      wasControlDown = onlyEditedFiles == null && event != null && event.isControlDown();
      pinned = !wasAltDown && !wasControlDown;
      KeyAdapter onKeyRelease = new KeyAdapter() {
        @Override
        public void keyReleased(@NotNull KeyEvent event) {
          int code = event.getKeyCode();
          if (wasAltDown && code == VK_ALT || wasControlDown && code == VK_CONTROL) {
            navigate(event);
          }
        }
      };
      boolean onlyEdited = Boolean.TRUE.equals(onlyEditedFiles);
      myTitle = title;
      mySpeedSearch = pinned ? new SwitcherSpeedSearch(this) : null;
      cbShowOnlyEditedFiles = !pinned || !Experiments.getInstance().isFeatureEnabled("recent.and.edited.files.together")
                                      ? null : new JCheckBox(IdeBundle.message("recent.files.checkbox.label"));

      SwitcherListRenderer renderer = new SwitcherListRenderer(this);

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

      CollectionListModel<Object> twModel = new CollectionListModel<>();
      List<SwitcherToolWindow> windows = renderer.getToolWindows();
      twShortcuts = createShortcuts(windows);
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

      toolWindows = new JBList<>(createModel(twModel, getNamer(), mySpeedSearch, pinned));
      toolWindows.setPreferredSize(new Dimension(JBUI.scale(200), toolWindows.getPreferredSize().height));

      toolWindows.setBorder(JBUI.Borders.empty(5, 5, 5, 20));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.setCellRenderer(renderer);
      toolWindows.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
      toolWindows.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(toolWindows);
      ListHoverListener.DEFAULT.addTo(toolWindows);
      ScrollingUtil.ensureSelectionExists(toolWindows);
      myClickListener.installOn(toolWindows);
      toolWindows.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(@NotNull ListSelectionEvent e) {
          if (!toolWindows.isSelectionEmpty() && !files.isSelectionEmpty()) {
            files.clearSelection();
          }
        }
      });

      final List<FileInfo> filesToShow = getFilesToShow(project, collectFiles(project, onlyEdited),
                                                        toolWindows.getModel().getSize(), pinned);
      final CollectionListModel<FileInfo> filesModel = new CollectionListModel<>();
      for (FileInfo editor : filesToShow) {
        filesModel.add(editor);
      }

      final VirtualFilesRenderer filesRenderer = new VirtualFilesRenderer(this) {
        final JPanel myPanel = new NonOpaquePanel(new BorderLayout());

        {
          myPanel.setBackground(UIUtil.getListBackground());
        }

        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList<? extends FileInfo> list,
                                                      FileInfo value, int index, boolean selected, boolean hasFocus) {
          Component c = super.getListCellRendererComponent(list, value, index, selected, selected);
          myPanel.removeAll();
          myPanel.add(c, BorderLayout.CENTER);

          // Note: Name=name rendered in cell, Description=path to file, as displayed in bottom panel
          myPanel.getAccessibleContext().setAccessibleName(c.getAccessibleContext().getAccessibleName());
          VirtualFile file = value.first;
          String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
          String location = FileUtil.getLocationRelativeToUserHome(presentableUrl);
          myPanel.getAccessibleContext().setAccessibleDescription(location);
          // update background of hovered list item
          if (!selected && index == ListHoverListener.getHoveredIndex(list)) {
            setBackground(JBUI.CurrentTheme.List.Hover.background(true));
          }
          return myPanel;
        }

        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends FileInfo> list,
                                             FileInfo value, int index, boolean selected, boolean hasFocus) {
          setPaintFocusBorder(false);
          super.customizeCellRenderer(list, value, index, selected, hasFocus);
        }
      };

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
          List<FileInfo> values = files.getSelectedValuesList();
          if (values != null && values.size() == 1) {
            VirtualFile file = values.get(0).first;
            String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
            pathLabel.setText(getTitle2Text(FileUtil.getLocationRelativeToUserHome(presentableUrl)));
          }
          else {
            pathLabel.setText(" ");
          }
        }
      };
      files = JBListWithOpenInRightSplit
        .createListWithOpenInRightSplitter(createModel(filesModel, FileInfo::getNameForRendering, mySpeedSearch, pinned), null, true);
      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getSelectionModel().addListSelectionListener(e -> {
        if (!files.isSelectionEmpty() && !toolWindows.isSelectionEmpty()) {
          toolWindows.getSelectionModel().clearSelection();
        }
      });

      files.getSelectionModel().addListSelectionListener(filesSelectionListener);

      files.setCellRenderer(filesRenderer);
      files.setBorder(JBUI.Borders.empty(5));
      files.addKeyListener(onKeyRelease);
      ScrollingUtil.installActions(files);
      ListHoverListener.DEFAULT.addTo(files);
      myClickListener.installOn(files);
      ScrollingUtil.ensureSelectionExists(files);

      if (filesModel.getSize() > 0) {
        files.setAlignmentY(1f);
        final JScrollPane pane = ScrollPaneFactory.createScrollPane(files, true);
        pane.setPreferredSize(new Dimension(Math.max(header.getPreferredSize().width - toolWindows.getPreferredSize().width,
                                                     files.getPreferredSize().width),
                                            20 * 20));
        Border border = JBUI.Borders.merge(
          JBUI.Borders.emptyLeft(9),
          new CustomLineBorder(SEPARATOR_COLOR, JBUI.insetsLeft(1)),
          true
        );
        pane.setBorder(border);
        addToCenter(pane);
        int selectionIndex = getFilesSelectedIndex(project, files, event == null || !event.isShiftDown());
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
      }
      addToTop(header);
      addToLeft(toolWindows);
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
        //noinspection SpellCheckingInspection
        String mnemonics = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < mnemonics.length(); i++) {
          registerToolWindowAction(mnemonics.substring(i, i + 1));
        }
      }
      setFocusCycleRoot(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

      new SwitcherListFocusAction(files, toolWindows, ListActions.Left.ID);
      new SwitcherListFocusAction(toolWindows, files, ListActions.Right.ID);

      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window == null) {
        window = WindowManager.getInstance().getFrame(project);
      }
      myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);

      SwitcherPanel old = project.getUserData(SWITCHER_KEY);
      if (old != null) old.cancel();
      project.putUserData(SWITCHER_KEY, this);

      myPopup.showInCenterOf(window);
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

    @NotNull
    private Function<? super Object, String> getNamer() {
      return value -> {
        if (value instanceof SwitcherListItem) {
          return ((SwitcherListItem)value).getTextAtLeft();
        }

        throw new IllegalStateException();
      };
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
                                                SwitcherPanel.SwitcherSpeedSearch speedSearch,
                                                boolean pinned) {
      ListModel<T> listModel;
      if (pinned) {
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

    static int getFilesSelectedIndex(Project project, JList<FileInfo> filesList, boolean forward) {
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      EditorWindow currentWindow = editorManager.getCurrentWindow();
      VirtualFile currentFile = currentWindow != null ? currentWindow.getSelectedFile() : null;

      ListModel<FileInfo> model = filesList.getModel();
      if (forward) {
        for (int i = 0; i < model.getSize(); i++) {
          FileInfo fileInfo = model.getElementAt(i);
          if (!isTheSameTab(currentWindow, currentFile, fileInfo)) {
            return i;
          }
        }
      }
      else {
        for (int i = model.getSize() - 1; i >= 0; i--) {
          FileInfo fileInfo = model.getElementAt(i);
          if (!isTheSameTab(currentWindow, currentFile, fileInfo)) {
            return i;
          }
        }
      }

      return -1;
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

    @NotNull
    private static Map<String, SwitcherToolWindow> createShortcuts(@NotNull List<SwitcherToolWindow> windows) {
      final Map<String, SwitcherToolWindow> keymap = new HashMap<>(windows.size());
      final List<SwitcherToolWindow> otherTW = new ArrayList<>();
      for (SwitcherToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(window.getWindow().getId());
        if (index >= '0' && index <= '9') {
          keymap.put(getIndexShortcut(index - '0'), window);
        }
        else {
          otherTW.add(window);
        }
      }
      int i = 0;
      for (SwitcherToolWindow window : otherTW) {
        String bestShortcut = getSmartShortcut(window, keymap);
        if (bestShortcut != null) {
          keymap.put(bestShortcut, window);
          continue;
        }

        while (keymap.get(getIndexShortcut(i)) != null) {
          i++;
        }
        keymap.put(getIndexShortcut(i), window);
        i++;
      }
      keymap.forEach((string, window) -> {
        if (!StringUtil.isEmpty(string)) {
          window.setMnemonic(string);
        }
      });
      return keymap;
    }

    @Nullable
    private static String getSmartShortcut(SwitcherToolWindow window, Map<String, SwitcherToolWindow> keymap) {
      String title = window.getTextAtLeft();
      if (StringUtil.isEmpty(title))
        return null;
      for (int i = 0; i < title.length(); i++) {
        char c = title.charAt(i);
        if (Character.isUpperCase(c)) {
          String shortcut = String.valueOf(c);
          if (keymap.get(shortcut) == null)
            return shortcut;
        }
      }
      return null;
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
      final JBList selectedList = getSelectedList();
      final int[] selected = selectedList.getSelectedIndices();
      Arrays.sort(selected);
      int selectedIndex = 0;
      for (int i = selected.length - 1; i >= 0; i--) {
        selectedIndex = selected[i];
        Object value = selectedList.getModel().getElementAt(selectedIndex);
        if (value instanceof FileInfo) {
          final FileInfo info = (FileInfo)value;
          final VirtualFile virtualFile = info.first;
          final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          final JList jList = getSelectedList();
          final EditorWindow wnd = findAppropriateWindow(info);
          if (wnd == null) {
            editorManager.closeFile(virtualFile, false, false);
          }
          else {
            editorManager.closeFile(virtualFile, wnd, false);
          }

          final IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(() -> {
            JComponent focusTarget = selectedList;
            if (selectedList.getModel().getSize() == 0) {
              focusTarget = selectedList == files ? toolWindows : files;
            }
            focusManager.requestFocus(focusTarget, true);
          }, 300);
          if (jList.getModel().getSize() == 1) {
            removeElementAt(jList, selectedIndex);
            this.remove(jList);
            final Dimension size = toolWindows.getSize();
            myPopup.setSize(new Dimension(size.width, myPopup.getSize().height));
          }
          else {
            removeElementAt(jList, selectedIndex);
            jList.setSize(jList.getPreferredSize());
          }
          if (pinned) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile);
          }
        }
        else if (value instanceof SwitcherListItem) {
          SwitcherListItem item = (SwitcherListItem)value;
          item.close(this);
        }
      }
      pack();
      myPopup.getContent().revalidate();
      myPopup.getContent().repaint();
      if (getSelectedList().getModel().getSize() > selectedIndex) {
        getSelectedList().setSelectedIndex(selectedIndex);
        getSelectedList().ensureIndexIsVisible(selectedIndex);
      }
    }

    private static void removeElementAt(@NotNull JList<?> jList, int index) {
      ListUtil.removeItem(jList.getModel(), index);
    }

    private void pack() {
      this.setSize(this.getPreferredSize());
      final JRootPane rootPane = SwingUtilities.getRootPane(this);
      Container container = this;
      do {
        container = container.getParent();
        container.setSize(container.getPreferredSize());
      }
      while (container != rootPane);
      container.getParent().setSize(container.getPreferredSize());
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

    void go(@Nullable InputEvent event) {
      go(event == null || !event.isShiftDown());
    }

    public void go(boolean forward) {
      JBList selected = getSelectedList();
      JList list = selected;
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

    public JBList<?> getSelectedList() {
      return getSelectedList(files);
    }

    @Nullable
    JBList getSelectedList(@Nullable JBList preferable) {
      return files.hasFocus() ? files : toolWindows.hasFocus() ? toolWindows : preferable;
    }

    private void updateFilesByCheckBox(@NotNull ItemEvent event) {
      boolean onlyEdited = ItemEvent.SELECTED == event.getStateChange();
      final boolean listWasSelected = files.getSelectedIndex() != -1;

      final List<FileInfo> filesToShow = getFilesToShow(project, collectFiles(project, onlyEdited),
                                                        toolWindows.getModel().getSize(), pinned);

      ListModel<FileInfo> model = files.getModel();
      ListUtil.removeAllItems(model);
      ListUtil.addAllItems(model, filesToShow);

      int selectionIndex = getFilesSelectedIndex(project, files, true);
      if (selectionIndex > -1 && listWasSelected) {
        files.setSelectedIndex(selectionIndex);
      }
      files.revalidate();
      files.repaint();
      // refresh the Recent Locations item
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
      else if (values.get(0) instanceof SwitcherListItem) {
        SwitcherListItem item = (SwitcherListItem)values.get(0);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> item.navigate(this, mode), ModalityState.current());
      }
      else {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          EditorWindow splitWindow = null;
          for (Object value : values) {
            if (value instanceof FileInfo) {
              final FileInfo info = (FileInfo)value;

              VirtualFile file = info.first;
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
              else if (info.second != null) {
                EditorWindow wnd = findAppropriateWindow(info);
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

    private @NotNull CustomShortcutSet getShortcuts(@NonNls String @NotNull ... keys) {
      return CustomShortcutSet.fromString(pinned ? keys : Stream.of(keys).map(key -> {
        StringBuilder sb = new StringBuilder();
        if (wasControlDown) sb.append("control ");
        if (wasAltDown) sb.append("alt ");
        return sb.append(key).toString();
      }).toArray(String[]::new));
    }

    private void registerAction(@NotNull Consumer<InputEvent> action, @NonNls String @NotNull ... keys) {
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          action.consume(event.getInputEvent());
        }
      }.registerCustomShortcutSet(getShortcuts(keys), this, myPopup);
    }

    private void registerSwingAction(@NonNls @NotNull String id, @NonNls String @NotNull ... keys) {
      registerAction(event -> SwingActionDelegate.performAction(id, getSelectedList(null)), keys);
    }

    private void registerToolWindowAction(@NonNls @NotNull String key) {
      registerAction(event -> {
        SwitcherToolWindow window = twShortcuts.get(key);
        if (window != null) {
          cancel();
          window.getWindow().activate(null, true, true);
        }
      }, key);
    }

    @Nullable
    private static EditorWindow findAppropriateWindow(@NotNull FileInfo info) {
      if (info.second == null) return null;
      if (UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE) {
        return info.second.getOwner().getCurrentWindow();
      }
      final EditorWindow[] windows = info.second.getOwner().getWindows();
      return ArrayUtil.contains(info.second, windows) ? info.second : windows.length > 0 ? windows[0] : null;
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
      protected Object getElementAt(int viewIndex) {
        ListModel<FileInfo> filesModel = myComponent.files.getModel();
        ListModel<Object> twModel = myComponent.toolWindows.getModel();
        if (viewIndex < filesModel.getSize()) return filesModel.getElementAt(viewIndex);
        return twModel.getElementAt(viewIndex - filesModel.getSize());
      }

      @Override
      protected String getElementText(Object element) {
        if (element instanceof SwitcherListItem) {
          return ((SwitcherListItem)element).getTextAtLeft();
        }
        else if (element instanceof FileInfo) {
          return ((FileInfo)element).getNameForRendering();
        }
        return "";
      }

      @Override
      protected void selectElement(final Object element, String selectedText) {
        if (element instanceof FileInfo) {
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
      protected Object findElement(@NotNull String s) {
        final List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
        return elements.isEmpty() ? null : elements.get(0).node;
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

  private static class VirtualFilesRenderer extends ColoredListCellRenderer<FileInfo> {
    private final SwitcherPanel mySwitcherPanel;
    boolean open;

    VirtualFilesRenderer(@NotNull SwitcherPanel switcherPanel) {
      mySwitcherPanel = switcherPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FileInfo> list,
                                         FileInfo value, int index, boolean selected, boolean hasFocus) {
      Project project = mySwitcherPanel.project;
      VirtualFile virtualFile = value.getFirst();
      String renderedName = value.getNameForRendering();
      setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, project));

      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
      open = FileEditorManager.getInstance(project).isFileOpen(virtualFile);

      boolean hasProblem = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);
      TextAttributes attributes =
        new TextAttributes(fileStatus.getColor(), null, hasProblem ? JBColor.red : null, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
      append(renderedName, SimpleTextAttributes.fromTextAttributes(attributes));

      // calc color the same way editor tabs do this, i.e. including EPs
      Color color = EditorTabPresentationUtil.getFileBackgroundColor(project, virtualFile);

      if (!selected && color != null) {
        setBackground(color);
      }
      SpeedSearchUtil.applySpeedSearchHighlighting(mySwitcherPanel, this, false, selected);

      if (Registry.is("show.last.visited.timestamps")) {
        IdeDocumentHistoryImpl.appendTimestamp(project, this, virtualFile);
      }
    }
  }

  static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    private final Project myProject;
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
}
