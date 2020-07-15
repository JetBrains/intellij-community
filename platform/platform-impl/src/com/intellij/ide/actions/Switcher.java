// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
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
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.PopupUpdateProcessorBase;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.util.function.Supplier;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.RecentFiles;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public final class Switcher extends AnAction implements DumbAware {
  public static final Key<SwitcherPanel> SWITCHER_KEY = Key.create("SWITCHER_KEY");
  private static volatile SwitcherPanel SWITCHER = null;
  private static final Color SEPARATOR_COLOR = JBColor.namedColor("Popup.separatorColor", new JBColor(Gray.xC0, Gray.x4B));
  private static final String TOGGLE_CHECK_BOX_ACTION_ID = "SwitcherRecentEditedChangedToggleCheckBox";

  private static final int MINIMUM_HEIGHT = JBUIScale.scale(400);
  private static final int MINIMUM_WIDTH = JBUIScale.scale(500);

  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";
  private static final Color ON_MOUSE_OVER_BG_COLOR = new JBColor(new Color(231, 242, 249), new Color(77, 80, 84));
  private static int CTRL_KEY;
  @Nullable public static final Runnable CHECKER = () -> {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.navigate(null);
      }
    }
  };
  @NotNull private static final CustomShortcutSet TW_SHORTCUT;

  static {
    Shortcut recentFiles = ArrayUtil.getFirstElement(getActiveKeymapShortcuts("RecentFiles").getShortcuts());
    List<Shortcut> shortcuts = new ArrayList<>();
    for (char ch = '0'; ch <= '9'; ch++) {
      shortcuts.add(CustomShortcutSet.fromString("control " + ch).getShortcuts()[0]);
    }
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      Shortcut shortcut = CustomShortcutSet.fromString("control " + ch).getShortcuts()[0];
      if (shortcut.equals(recentFiles)) continue;
      shortcuts.add(shortcut);
    }
    TW_SHORTCUT = new CustomShortcutSet(shortcuts.toArray(Shortcut.EMPTY_ARRAY));

    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(@NotNull AWTEvent event) {
        ToolWindow tw;
        if (SWITCHER != null && event instanceof KeyEvent && !SWITCHER.isPinnedMode()) {
          final KeyEvent keyEvent = (KeyEvent)event;
          if (event.getID() == KEY_RELEASED && keyEvent.getKeyCode() == CTRL_KEY) {
            ApplicationManager.getApplication().invokeLater(CHECKER, ModalityState.current());
          }
          else if (event.getID() == KEY_PRESSED && event != INIT_EVENT
                   && (tw = SWITCHER.twShortcuts.get(String.valueOf((char)keyEvent.getKeyCode()))) != null) {
            SWITCHER.myPopup.closeOk(null);
            tw.activate(null, true, true);
          }
        }
        return false;
      }
    }, null);
  }

  @NonNls private static final String SWITCHER_TITLE = "Switcher";
  @NonNls private static InputEvent INIT_EVENT;

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    boolean isNewSwitcher = false;
    synchronized (Switcher.class) {
      INIT_EVENT = e.getInputEvent();
      if (SWITCHER != null && SWITCHER.isPinnedMode()) {
        SWITCHER.cancel();
        SWITCHER = null;
      }
      if (SWITCHER == null) {
        isNewSwitcher = true;
        // Assigns SWITCHER field
        boolean moveBack = e.getInputEvent() != null && e.getInputEvent().isShiftDown();
        createAndShowSwitcher(project, SWITCHER_TITLE, IdeActions.ACTION_SWITCHER, false, false, !moveBack);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
      }
    }

    assert SWITCHER != null;
    if (!SWITCHER.isPinnedMode()) {
      if (isNewSwitcher && !FileEditorManagerEx.getInstanceEx(project).hasOpenedFile()) {
        SWITCHER.files.setSelectedIndex(0);
      }

      if (!isNewSwitcher) {
        if (e.getInputEvent() != null && e.getInputEvent().isShiftDown()) SWITCHER.goBack();
        else SWITCHER.goForward();
      }
    }
  }

  /**
   * @deprecated Please use {@link Switcher#createAndShowSwitcher(AnActionEvent, String, String, boolean, boolean)}
   */
  @Deprecated
  @Nullable
  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull String title, boolean pinned, final VirtualFile @Nullable [] vFiles) {
    return createAndShowSwitcher(e, title, "RecentFiles", pinned, vFiles != null);
  }

  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull String title, @NonNls @NotNull String actionId, boolean onlyEdited, boolean pinned) {
    if (SWITCHER != null && Objects.equals(SWITCHER.myTitle, title)) return null;

    Project project = e.getProject();
    boolean moveBack = e.getInputEvent() != null && e.getInputEvent().isShiftDown();
    return project == null ? null : createAndShowSwitcher(project, title, actionId, onlyEdited, pinned, !moveBack);
  }

  @Nullable
  private static SwitcherPanel createAndShowSwitcher(@NotNull Project project,
                                                     @NotNull String title,
                                                     @NotNull String actionId,
                                                     boolean onlyEdited,
                                                     boolean pinned,
                                                     boolean moveForward) {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.cancel();
      }
      SWITCHER = new SwitcherPanel(project, title, actionId, onlyEdited, pinned, moveForward);
      project.putUserData(SWITCHER_KEY, SWITCHER);
      return SWITCHER;
    }
  }

  public static class ToggleCheckBoxAction extends DumbAwareAction implements DumbAware, LightEditCompatible {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      SwitcherPanel switcherPanel = SWITCHER_KEY.get(project);
      if (switcherPanel != null) {
        switcherPanel.toggleShowEditedFiles();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      e.getPresentation().setEnabledAndVisible(SWITCHER_KEY.get(project) != null);
    }
  }

  public static class IterateItemsAction extends DumbAwareAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      SwitcherPanel switcherPanel = SWITCHER_KEY.get(project);
      if (switcherPanel != null) {
        switcherPanel.goForward();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      e.getPresentation().setEnabledAndVisible(SWITCHER_KEY.get(project) != null);
    }
  }

  public static class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener, DataProvider,
                                                              QuickSearchComponent, Disposable {
    static final int SWITCHER_ELEMENTS_LIMIT = 30;

    static final Object RECENT_LOCATIONS = new Object();
    final JBPopup myPopup;
    final JBList<Object> toolWindows;
    final JBList<FileInfo> files;
    final ToolWindowManager toolWindowManager;
    JBCheckBox myShowOnlyEditedFilesCheckBox;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel myTopPanel;
    final JPanel descriptions;
    final Project project;
    private final boolean myPinned;
    final Map<String, ToolWindow> twShortcuts;
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


    private class MyFocusTraversalPolicy extends FocusTraversalPolicy {

      @Override
      public Component getComponentAfter(Container aContainer, Component aComponent) {
        return aComponent == toolWindows ? files : toolWindows;
      }

      @Override
      public Component getComponentBefore(Container aContainer, Component aComponent) {
        return aComponent == toolWindows ? files : toolWindows;
      }

      @Override
      public Component getFirstComponent(Container aContainer) {
        return toolWindows;
      }

      @Override
      public Component getLastComponent(Container aContainer) {
        return files;
      }

      @Override
      public Component getDefaultComponent(Container aContainer) {
        return files;
      }
    }

    private static void exchangeSelectionState (JBList toClear, JBList toSelect) {
      if (toSelect.getModel().getSize() > 0) {
        int index = Math.min(toClear.getSelectedIndex(), toSelect.getModel().getSize() - 1);
        toSelect.setSelectedIndex(index);
        toSelect.ensureIndexIsVisible(index);
        toClear.clearSelection();
      }
    }

    private class MyToolWindowsListFocusListener extends FocusAdapter {
      @Override
      public void focusGained(FocusEvent e) {
        exchangeSelectionState(files, toolWindows);
      }
    }

    private class MyFilesListFocusListener extends FocusAdapter {
      @Override
      public void focusGained(FocusEvent e) {
        exchangeSelectionState(toolWindows, files);
      }
    }

    final ClickListener myClickListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (myPinned && (e.isControlDown() || e.isMetaDown() || e.isShiftDown())) return false;
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

    @SuppressWarnings({"ConstantConditions"})
    SwitcherPanel(@NotNull final Project project, @NotNull String title, @NotNull String actionId, boolean onlyEdited, boolean pinned,
                  boolean moveForward)
    {
      setLayout(new BorderLayout());
      this.project = project;
      myTitle = title;
      myPinned = pinned;
      mySpeedSearch = pinned ? new SwitcherSpeedSearch(this) : null;

      setBorder(JBUI.Borders.empty());
      setBackground(JBColor.background());
      pathLabel.setHorizontalAlignment(SwingConstants.LEFT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont(Math.max(10f, font.getSize() - 4f)));

      descriptions = new JPanel(new BorderLayout());

      pathLabel.setBorder(JBUI.CurrentTheme.Advertiser.border());
      pathLabel.setForeground(JBUI.CurrentTheme.Advertiser.foreground());
      pathLabel.setBackground(JBUI.CurrentTheme.Advertiser.background());
      pathLabel.setOpaque(true);

      descriptions.setBorder(new CustomLineBorder(JBUI.CurrentTheme.Advertiser.borderColor(), JBUI.insetsTop(1)));
      descriptions.add(pathLabel, BorderLayout.CENTER);
      toolWindowManager = ToolWindowManager.getInstance(project);
      CollectionListModel<Object> twModel = new CollectionListModel<>();
      List<ActivateToolWindowAction> actions = ToolWindowsGroup.getToolWindowActions(project, true);
      List<ToolWindow> windows = new ArrayList<>();
      for (ActivateToolWindowAction action : actions) {
        ToolWindow tw = toolWindowManager.getToolWindow(action.getToolWindowId());
        if (tw.isAvailable()) {
          windows.add(tw);
        }
      }
      twShortcuts = createShortcuts(windows);
      final Map<ToolWindow, String> map = ContainerUtil.reverseMap(twShortcuts);
      windows.sort((o1, o2) -> StringUtil.compare(map.get(o1), map.get(o2), false));
      for (ToolWindow window : windows) {
        twModel.add(window);
      }
      twModel.add(RECENT_LOCATIONS);

      toolWindows = createList(twModel, getNamer(), mySpeedSearch, pinned);
      toolWindows.addFocusListener(new MyToolWindowsListFocusListener());
      toolWindows.setPreferredSize(new Dimension(JBUI.scale(200), toolWindows.getPreferredSize().height));

      toolWindows.setBorder(JBUI.Borders.empty(5, 5, 5, 20));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.setCellRenderer(new SwitcherToolWindowsListRenderer(mySpeedSearch, map, myPinned, showEdited()) {
        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, selected);
          if (selected) {
            return renderer;
          }
          final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ? ON_MOUSE_OVER_BG_COLOR : list.getBackground();
          UIUtil.changeBackGround(renderer, bgColor);
          return renderer;
        }
      });
      toolWindows.addKeyListener(this);
      ScrollingUtil.installActions(toolWindows);
      toolWindows.addMouseListener(this);
      toolWindows.addMouseMotionListener(this);
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
          if (!selected && list == mouseMoveSrc && index == mouseMoveListIndex) {
            setBackground(ON_MOUSE_OVER_BG_COLOR);
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
        @Nullable
        private String getTitle2Text(@Nullable String fullText) {
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
      files = createList(filesModel, FileInfo::getNameForRendering, mySpeedSearch, pinned);
      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getSelectionModel().addListSelectionListener(e -> {
        if (!files.isSelectionEmpty() && !toolWindows.isSelectionEmpty()) {
          toolWindows.getSelectionModel().clearSelection();
        }
      });

      files.getSelectionModel().addListSelectionListener(filesSelectionListener);

      files.setCellRenderer(filesRenderer);
      files.setBorder(JBUI.Borders.empty(5));
      files.addKeyListener(this);
      ScrollingUtil.installActions(files);
      files.addMouseListener(this);
      files.addMouseMotionListener(this);
      files.addFocusListener(new MyFilesListFocusListener());
      myClickListener.installOn(files);
      ScrollingUtil.ensureSelectionExists(files);

      myShowOnlyEditedFilesCheckBox = new MyCheckBox(TOGGLE_CHECK_BOX_ACTION_ID, onlyEdited);
      myTopPanel = createTopPanel(myShowOnlyEditedFilesCheckBox,
                                  isCheckboxMode() ? IdeBundle.message("title.popup.recent.files") : title,
                                  pinned);
      if (isCheckboxMode()) {
        myShowOnlyEditedFilesCheckBox.addActionListener(e -> setShowOnlyEditedFiles(myShowOnlyEditedFilesCheckBox.isSelected()));
        myShowOnlyEditedFilesCheckBox.addActionListener(e -> toolWindows.repaint());
      }
      else {
        myShowOnlyEditedFilesCheckBox.setEnabled(false);
        myShowOnlyEditedFilesCheckBox.setVisible(false);
      }

      this.add(myTopPanel, BorderLayout.NORTH);
      this.add(toolWindows, BorderLayout.WEST);
      if (filesModel.getSize() > 0) {
        files.setAlignmentY(1f);
        final JScrollPane pane = ScrollPaneFactory.createScrollPane(files, true);
        pane.setPreferredSize(new Dimension(Math.max(myTopPanel.getPreferredSize().width - toolWindows.getPreferredSize().width,
                                                     files.getPreferredSize().width),
                                            20 * 20));
        Border border = JBUI.Borders.merge(
          JBUI.Borders.emptyLeft(9),
          new CustomLineBorder(SEPARATOR_COLOR, JBUI.insetsLeft(1)),
          true
        );
        pane.setBorder(border);
        this.add(pane, BorderLayout.CENTER);
        int selectionIndex = getFilesSelectedIndex(project, files, moveForward);
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
      }
      this.add(descriptions, BorderLayout.SOUTH);

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_SWITCHER).getShortcutSet();
      final int modifiers = getModifiers(shortcutSet);
      final boolean isAlt = (modifiers & Event.ALT_MASK) != 0;
      CTRL_KEY = isAlt ? VK_ALT : VK_CONTROL;
      files.addKeyListener(ArrayUtil.getLastElement(getKeyListeners()));
      toolWindows.addKeyListener(ArrayUtil.getLastElement(getKeyListeners()));
      KeymapUtil.reassignAction(toolWindows, getKeyStroke(VK_UP, 0), getKeyStroke(VK_UP, CTRL_DOWN_MASK), WHEN_FOCUSED, false);
      KeymapUtil.reassignAction(toolWindows, getKeyStroke(VK_DOWN, 0), getKeyStroke(VK_DOWN, CTRL_DOWN_MASK), WHEN_FOCUSED, false);
      KeymapUtil.reassignAction(files, getKeyStroke(VK_UP, 0), getKeyStroke(VK_UP, CTRL_DOWN_MASK), WHEN_FOCUSED, false);
      KeymapUtil.reassignAction(files, getKeyStroke(VK_DOWN, 0), getKeyStroke(VK_DOWN, CTRL_DOWN_MASK), WHEN_FOCUSED, false);

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
        .setCancelCallback(() -> {
          Container popupFocusAncestor = getPopupFocusAncestor();
          if (popupFocusAncestor != null) popupFocusAncestor.setFocusTraversalPolicy(null);
          SWITCHER = null;
          return true;
        }).createPopup();
      Disposer.register(myPopup, this);

      if (isPinnedMode()) {
        new DumbAwareAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
              mySpeedSearch.hidePopup();
              Object[] elements = mySpeedSearch.getAllElements();
              if (elements != null && elements.length > 0) {
                mySpeedSearch.selectElement(elements[0], "");
              }
            }
            else {
              myPopup.cancel();
            }
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), this, myPopup);
      }
      if (!myPinned) {
        new DumbAwareAction(IdeBundle.messagePointer("action.AnActionButton.text.suppress.all.actions.to.activate.a.toolwindow")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            //suppress all actions to activate a toolwindow : IDEA-71277
          }
        }.registerCustomShortcutSet(TW_SHORTCUT, this, myPopup);
      }
      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window == null) {
        window = WindowManager.getInstance().getFrame(project);
      }
      myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
      myPopup.showInCenterOf(window);

      Container popupFocusAncestor = getPopupFocusAncestor();
      popupFocusAncestor.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, "RIGHT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, "LEFT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, "control RIGHT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, "control LEFT");

      fromListToList(toolWindows, files);
      fromListToList(files, toolWindows);
    }

    @Override
    public void dispose() {
      synchronized (Switcher.class) {
        SWITCHER = null;
        project.putUserData(SWITCHER_KEY, null);
      }
    }

    @NotNull
    private Supplier<Boolean> showEdited() {
      return () -> myShowOnlyEditedFilesCheckBox != null && myShowOnlyEditedFilesCheckBox.isSelected();
    }

    @NotNull
    private Function<? super Object, String> getNamer() {
      return value -> {
        if (value instanceof ToolWindow) {
          return ((ToolWindow)value).getStripeTitle();
        }
        if (value == RECENT_LOCATIONS) {
          return getRecentLocationsLabel(showEdited());
        }

        throw new IllegalStateException();
      };
    }

    static String getRecentLocationsLabel(@NotNull Supplier<Boolean> showEdited) {
      return showEdited.get() ? IdeBundle.message("recent.locations.changed.locations") : IdeBundle.message("recent.locations.popup.title");
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

    @NotNull
    private static <T> JBList<T> createList(CollectionListModel<T> baseModel,
                                            Function<? super T, String> namer,
                                            SwitcherSpeedSearch speedSearch,
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
      return new JBList<>(listModel);
    }

    private static void fromListToList(JBList from, JBList to) {
      AbstractAction action = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent event) {
          to.requestFocus();
        }
      };
      ActionMap map = from.getActionMap();
      map.put(ListActions.Left.ID, action);
      map.put(ListActions.Right.ID, action);
    }

    private Container getPopupFocusAncestor() {
      return myPopup.isDisposed() ? null : myPopup.getContent().getFocusCycleRootAncestor();
    }

    static @NotNull List<VirtualFile> collectFiles(@NotNull Project project, boolean onlyEdited) {
      return onlyEdited ? IdeDocumentHistory.getInstance(project).getChangedFiles() : getRecentFiles(project);
    }

    @NotNull
    static List<FileInfo> getFilesToShow(@NotNull Project project, @NotNull List<VirtualFile> filesForInit,
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
      if (filesData.size() <= 1 || pinned) {
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
    private static JPanel createTopPanel(@NotNull JBCheckBox showOnlyEditedFilesCheckBox,
                                         @NotNull String title,
                                         boolean isMovable) {
      JPanel topPanel = new CaptionPanel();
      JBLabel titleLabel = new JBLabel(title);
      titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
      topPanel.add(titleLabel, BorderLayout.WEST);
      topPanel.add(showOnlyEditedFilesCheckBox, BorderLayout.EAST);

      Dimension size = topPanel.getPreferredSize();
      size.height = JBUIScale.scale(29);
      size.width = titleLabel.getPreferredSize().width + showOnlyEditedFilesCheckBox.getPreferredSize().width + JBUIScale.scale(50);
      topPanel.setPreferredSize(size);
      topPanel.setMinimumSize(size);
      topPanel.setBorder(JBUI.Borders.empty(5, 8));

      if (isMovable) {
        WindowMoveListener moveListener = new WindowMoveListener(topPanel);
        topPanel.addMouseListener(moveListener);
        topPanel.addMouseMotionListener(moveListener);
      }

      return topPanel;
    }

    private static void  addFocusTraversalKeys (Container focusCycleRoot, int focusTraversalType, String keyStroke) {
      Set<AWTKeyStroke> focusTraversalKeySet = focusCycleRoot.getFocusTraversalKeys(focusTraversalType);

      Set<AWTKeyStroke> set = new HashSet<>(focusTraversalKeySet);
      set.add(getKeyStroke(keyStroke));
      focusCycleRoot.setFocusTraversalKeys(focusTraversalType, set);
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
    private static Map<String, ToolWindow> createShortcuts(@NotNull List<ToolWindow> windows) {
      final Map<String, ToolWindow> keymap = new HashMap<>(windows.size());
      final List<ToolWindow> otherTW = new ArrayList<>();
      for (ToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(window.getId());
        if (index >= '0' && index <= '9') {
          keymap.put(getIndexShortcut(index - '0'), window);
        }
        else {
          otherTW.add(window);
        }
      }
      int i = 0;
      for (ToolWindow window : otherTW) {
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
      return keymap;
    }

    @Nullable
    private static String getSmartShortcut(ToolWindow window, Map<String, ToolWindow> keymap) {
      String title = window.getStripeTitle();
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

    private static int getModifiers(@Nullable ShortcutSet shortcutSet) {
      if (shortcutSet == null
          || shortcutSet.getShortcuts().length == 0
          || !(shortcutSet.getShortcuts()[0] instanceof KeyboardShortcut)) {
        return Event.CTRL_MASK;
      }
      return ((KeyboardShortcut)shortcutSet.getShortcuts()[0]).getFirstKeyStroke().getModifiers();
    }

    @Override
    public void keyTyped(@NotNull KeyEvent e) {}

    @Override
    public void keyReleased(@NotNull KeyEvent e) {
      boolean ctrl = e.getKeyCode() == CTRL_KEY;
      if ((ctrl && isAutoHide())) {
        navigate(e);
      }
    }

    KeyEvent lastEvent;

    @Override
    public void keyPressed(@NotNull KeyEvent e) {
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive() || lastEvent == e) return;
      lastEvent = e;
      switch (e.getKeyCode()) {
        case VK_DELETE:
        case VK_BACK_SPACE: // Mac users
        case VK_Q:
          closeTabOrToolWindow();
          break;
        case VK_ESCAPE:
          cancel();
          break;
        case VK_ENTER:
          if (mySpeedSearch == null) navigate(e);
          break;
      }
    }

    private void closeTabOrToolWindow() {
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
          if (isPinnedMode()) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile);
          }
        }
        else if (value instanceof ToolWindow) {
          final ToolWindow toolWindow = (ToolWindow)value;
          if (toolWindowManager instanceof ToolWindowManagerImpl) {
            ((ToolWindowManagerImpl)toolWindowManager).hideToolWindow(toolWindow.getId(), false, false);
          }
          else {
            toolWindow.hide(null);
          }
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

    boolean isCheckboxMode() {
      return isPinnedMode() && Experiments.getInstance().isFeatureEnabled("recent.and.edited.files.together");
    }

    void toggleShowEditedFiles() {
      myShowOnlyEditedFilesCheckBox.doClick();
    }

    void setShowOnlyEditedFiles(boolean onlyEdited) {
      if (myShowOnlyEditedFilesCheckBox.isSelected() != onlyEdited) {
        myShowOnlyEditedFilesCheckBox.setSelected(onlyEdited);
      }

      final boolean listWasSelected = files.getSelectedIndex() != -1;

      final List<FileInfo> filesToShow = getFilesToShow(project, collectFiles(project, onlyEdited),
                                                        toolWindows.getModel().getSize(), isPinnedMode());

      ListModel<FileInfo> model = files.getModel();
      ListUtil.removeAllItems(model);
      ListUtil.addAllItems(model, filesToShow);

      int selectionIndex = getFilesSelectedIndex(project, files, true);
      if (selectionIndex > -1 && listWasSelected) {
        files.setSelectedIndex(selectionIndex);
      }
      files.revalidate();
      files.repaint();
    }

    void navigate(final InputEvent e) {
      boolean openInNewWindow = e != null && e.isShiftDown() && e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == VK_ENTER;
      List<?> values = getSelectedList().getSelectedValuesList();
      String searchQuery = mySpeedSearch != null ? mySpeedSearch.getEnteredPrefix() : null;
      myPopup.cancel(null);
      if (values.isEmpty()) {
        tryToOpenFileSearch(e, searchQuery);
      }
      else if (values.get(0) == RECENT_LOCATIONS) {
        RecentLocationsAction.showPopup(project, myShowOnlyEditedFilesCheckBox.isSelected());

      } else if (values.get(0) instanceof ToolWindow) {
        ToolWindow toolWindow = (ToolWindow)values.get(0);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> toolWindow.activate(null, true, true),
                                                                    ModalityState.current());
      }
      else {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          for (Object value : values) {
            if (value instanceof FileInfo) {
              final FileInfo info = (FileInfo)value;

              VirtualFile file = info.first;
              if (openInNewWindow) {
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
                  LightEditFeatureUsagesUtil.logFileOpen(RecentFiles);
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
        myPopup.cancel();
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

    @Nullable
    private static EditorWindow findAppropriateWindow(@NotNull FileInfo info) {
      if (info.second == null) return null;
      if (UISettings.getInstance().getEditorTabPlacement() == UISettings.TABS_NONE) {
        return info.second.getOwner().getCurrentWindow();
      }
      final EditorWindow[] windows = info.second.getOwner().getWindows();
      return ArrayUtil.contains(info.second, windows) ? info.second : windows.length > 0 ? windows[0] : null;
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
    }

    private boolean mouseMovedFirstTime = true;
    private JList mouseMoveSrc = null;
    private int mouseMoveListIndex = -1;

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (mouseMovedFirstTime) {
        mouseMovedFirstTime = false;
        return;
      }
      final Object source = e.getSource();
      boolean changed = false;
      if (source instanceof JList) {
        JList list = (JList)source;
        int index = list.locationToIndex(e.getPoint());
        if (0 <= index && index < list.getModel().getSize()) {
          mouseMoveSrc = list;
          mouseMoveListIndex = index;
          changed = true;
        }
      }
      if (!changed) {
        mouseMoveSrc = null;
        mouseMoveListIndex = -1;
      }

      repaintLists();
    }

    private void repaintLists() {
      toolWindows.repaint();
      files.repaint();
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      mouseMoveSrc = null;
      mouseMoveListIndex = -1;
      repaintLists();
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
    }

    private static class SwitcherSpeedSearch extends SpeedSearchBase<SwitcherPanel> implements PropertyChangeListener {

      SwitcherSpeedSearch(@NotNull SwitcherPanel switcher) {
        super(switcher);
        addChangeListener(this);
        setComparator(new SpeedSearchComparator(false, true));
      }

      @Override
      protected void processKeyEvent(@NotNull KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == VK_ENTER) {
          SWITCHER.navigate(e);
          e.consume();
          return;
        }
        if (keyCode == VK_LEFT || keyCode == VK_RIGHT) {
          return;
        }
        super.processKeyEvent(e);
      }

      @Override
      protected int getSelectedIndex() {
        return myComponent.isFilesSelected()
               ? myComponent.files.getSelectedIndex()
               : myComponent.files.getModel().getSize() + myComponent.toolWindows.getSelectedIndex();
      }

      @Override
      protected Object @NotNull [] getAllElements() {
        ListModel filesModel = myComponent.files.getModel();
        Object[] files = new Object[filesModel.getSize()];
        for (int i = 0; i < files.length; i++) {
          files[i] = filesModel.getElementAt(i);
        }

        ListModel twModel = myComponent.toolWindows.getModel();
        Object[] toolWindows = new Object[twModel.getSize()];
        for (int i = 0; i < toolWindows.length; i++) {
          toolWindows[i] = twModel.getElementAt(i);
        }

        Object[] elements = new Object[files.length + toolWindows.length];
        System.arraycopy(files, 0, elements, 0, files.length);
        System.arraycopy(toolWindows, 0, elements, files.length, toolWindows.length);

        return elements;
      }


      @Override
      protected String getElementText(Object element) {
        if (element instanceof ToolWindow) {
          return ((ToolWindow)element).getStripeTitle();
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
          myComponent.myPopup.cancel();
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

    public boolean isAutoHide() {
      return !myPinned;
    }

    public boolean isPinnedMode() {
      return myPinned;
    }
  }

  private static final class MyCheckBox extends JBCheckBox {
    private MyCheckBox(@NotNull String actionId, boolean selected) {
      super(layoutText(actionId), selected);
      setOpaque(false);
      setFocusable(false);
    }

    private static String layoutText(@NotNull String actionId) {
      String text = "<html>" + IdeBundle.message("recent.files.checkbox.label");
      ShortcutSet shortcuts = getActiveKeymapShortcuts(actionId);
      if (shortcuts.getShortcuts().length > 0) {
        text += " <font color=\"" + RecentLocationsAction.Holder.SHORTCUT_HEX_COLOR + "\">"
                + KeymapUtil.getShortcutsText(shortcuts.getShortcuts()) + "</font>"
                + "</html>";
      }
      return text;
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

    String getNameForRendering() {
      if (myNameForRendering == null) {
        // Recently changed files would also be taken into account (not only open 'visible' files)
        myNameForRendering = EditorTabPresentationUtil.getUniqueEditorTabTitle(myProject, first, second);
      }
      return myNameForRendering;
    }
  }
}
