// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "SSBasedInspection"})
public class Switcher extends AnAction implements DumbAware {
  private static volatile SwitcherPanel SWITCHER = null;
  private static final Color BORDER_COLOR = Gray._135;
  private static final Color SEPARATOR_COLOR = new JBColor(BORDER_COLOR.brighter(), Gray._75);
  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";
  private static final Color ON_MOUSE_OVER_BG_COLOR = new JBColor(new Color(231, 242, 249), new Color(77, 80, 84));
  private static int CTRL_KEY;
  private static int ALT_KEY;
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
    List<Shortcut> shortcuts = ContainerUtil.newArrayList();
    for (char ch = '0'; ch <= '9'; ch++) {
      shortcuts.add(CustomShortcutSet.fromString("control " + ch).getShortcuts()[0]);
    }
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      Shortcut shortcut = CustomShortcutSet.fromString("control " + ch).getShortcuts()[0];
      if (shortcut.equals(recentFiles)) continue;
      shortcuts.add(shortcut);
    }
    TW_SHORTCUT = new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()]));

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
        SWITCHER = createAndShowSwitcher(e, SWITCHER_TITLE, false, null);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
      }
    }

    assert SWITCHER != null;
    if (!SWITCHER.isPinnedMode()) {
      if (e.getInputEvent().isShiftDown()) {
        SWITCHER.goBack();
      }
      else {
        if (isNewSwitcher && !FileEditorManagerEx.getInstanceEx(project).hasOpenedFile()) {
          SWITCHER.files.setSelectedIndex(0);
        }
        else {
          SWITCHER.goForward();
        }
      }
    }
  }

  @Nullable
  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull String title, boolean pinned, @Nullable final VirtualFile[] vFiles) {
    Project project = getEventProject(e);
    if (SWITCHER != null && Comparing.equal(SWITCHER.myTitle, title)) {
      SWITCHER.goForward();
      return null;
    }
    return project == null ? null : createAndShowSwitcher(project, title, pinned, vFiles);
  }

  @Nullable
  private static SwitcherPanel createAndShowSwitcher(@NotNull Project project,
                                                     @NotNull String title,
                                                     boolean pinned,
                                                     @Nullable final VirtualFile[] vFiles) {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.cancel();
      }
      SWITCHER = new SwitcherPanel(project, title, pinned) {
        @NotNull
        @Override
        protected VirtualFile[] getFiles(@NotNull Project project) {
          return vFiles != null ? vFiles : super.getFiles(project);
        }
      };
      return SWITCHER;
    }
  }

  public static class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener, DataProvider {
    final JBPopup myPopup;
    final JBList toolWindows;
    final JBList files;
    final JPanel separator;
    final ToolWindowManager twManager;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel descriptions;
    final Project project;
    private final boolean myPinned;
    final Map<String, ToolWindow> twShortcuts;
    final Alarm myAlarm;
    final SwitcherSpeedSearch mySpeedSearch;
    final String myTitle;

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
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

    @SuppressWarnings({"ManualArrayToCollectionCopy", "ConstantConditions"})
    SwitcherPanel(@NotNull final Project project, @NotNull String title, boolean pinned) {
      setLayout(new SwitcherLayouter());
      this.project = project;
      myTitle = title;
      myPinned = pinned;
      mySpeedSearch = pinned ? new SwitcherSpeedSearch() : null;

      //setFocusable(true);
      //addKeyListener(this);
      setBorder(JBUI.Borders.empty());
      setBackground(JBColor.background());
      pathLabel.setHorizontalAlignment(SwingConstants.LEFT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont(Math.max(10f, font.getSize() - 4f)));

      descriptions = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(@NotNull Graphics g) {
          super.paintComponent(g);
          g.setColor(UIUtil.isUnderDarcula() ? SEPARATOR_COLOR : BORDER_COLOR);
          UIUtil.drawLine(g, 0, 0, getWidth(), 0);
        }
      };

      descriptions.setBorder(JBUI.Borders.empty(1, 4));
      descriptions.add(pathLabel, BorderLayout.CENTER);
      twManager = ToolWindowManager.getInstance(project);
      DefaultListModel twModel = new DefaultListModel();
      List<ActivateToolWindowAction> actions = ToolWindowsGroup.getToolWindowActions(project, true);
      List<ToolWindow> windows = ContainerUtil.newArrayList();
      for (ActivateToolWindowAction action : actions) {
        ToolWindow tw = twManager.getToolWindow(action.getToolWindowId());
        if (tw.isAvailable()) {
          windows.add(tw);
        }
      }
      twShortcuts = createShortcuts(windows);
      final Map<ToolWindow, String> map = ContainerUtil.reverseMap(twShortcuts);
      Collections.sort(windows, (o1, o2) -> StringUtil.compare(map.get(o1), map.get(o2), false));
      for (ToolWindow window : windows) {
        twModel.addElement(window);
      }

      toolWindows = new JBList(twModel);
      toolWindows.addFocusListener(new MyToolWindowsListFocusListener());
      if (pinned) {
        new NameFilteringListModel<ToolWindow>(toolWindows, window -> window.getStripeTitle(), s -> !mySpeedSearch.isPopupActive()
                                                                                                || StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
                                                                                                || mySpeedSearch.getComparator().matchingFragments(mySpeedSearch.getEnteredPrefix(), s) != null, mySpeedSearch);
      }

      toolWindows.setBorder(JBUI.Borders.empty(5, 5, 5, 20));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.setCellRenderer(new SwitcherToolWindowsListRenderer(mySpeedSearch, map, myPinned) {
        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList list,
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

      separator = new JPanel() {
        @Override
        protected void paintComponent(@NotNull Graphics g) {
          super.paintComponent(g);
          g.setColor(SEPARATOR_COLOR);
          UIUtil.drawLine(g, 0, 0, 0, getHeight());
        }
      };
      separator.setBackground(toolWindows.getBackground());

      int selectionIndex = -1;
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      final ArrayList<FileInfo> filesData = new ArrayList<>();
      final ArrayList<FileInfo> editors = new ArrayList<>();
      if (!pinned) {
        if (UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE) {
          for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
            editors.add(new FileInfo(pair.first, pair.second, project));
          }
        }
      }
      if (editors.size() < 2 || isPinnedMode()) {
        if (isPinnedMode() && editors.size() > 1) {
          filesData.addAll(editors);
        }
        final VirtualFile[] recentFiles = ArrayUtil.reverseArray(getFiles(project));
        final int maxFiles = Math.max(editors.size(), recentFiles.length);
        final int len = isPinnedMode() ? recentFiles.length : Math.min(toolWindows.getModel().getSize(), maxFiles);
        boolean firstRecentMarked = false;
        final List<VirtualFile> selectedFiles = Arrays.asList(editorManager.getSelectedFiles());
        for (int i = 0; i < len; i++) {
          if (isPinnedMode()
              && selectedFiles.contains(recentFiles[i])
              && UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE) {
            continue;
          }

          final FileInfo info = new FileInfo(recentFiles[i], null, project);
          boolean add = true;
          if (isPinnedMode()) {
            for (FileInfo fileInfo : filesData) {
              if (fileInfo.first.equals(info.first)) {
                add = false;
                break;
              }
            }
          }
          if (add) {
            filesData.add(info);
            if (!firstRecentMarked) {
              selectionIndex = filesData.size() - 1;
              if (selectionIndex != 0 || UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE || !isPinnedMode()) {
                firstRecentMarked = true;
              }
            }
          }
        }
        //if (editors.size() == 1) selectionIndex++;
        if (editors.size() == 1 && (filesData.isEmpty() || !editors.get(0).getFirst().equals(filesData.get(0).getFirst()))) {
          filesData.add(0, editors.get(0));
        }
      } else {
        for (int i = 0; i < Math.min(30, editors.size()); i++) {
          filesData.add(editors.get(i));
        }
      }

      final DefaultListModel filesModel = new DefaultListModel();
      for (FileInfo editor : filesData) {
        filesModel.addElement(editor);
      }

      final VirtualFilesRenderer filesRenderer = new VirtualFilesRenderer(this) {
        JPanel myPanel = new JPanel(new BorderLayout());
        JLabel myLabel = new JLabel() {
          @Override
          protected void paintComponent(@NotNull Graphics g) {
            GraphicsConfig config = new GraphicsConfig(g);
            ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            super.paintComponent(g);
            config.restore();
          }
        };

        {
          myPanel.setOpaque(false);
          myPanel.setBackground(UIUtil.getListBackground());
          myLabel.setText("* ");
        }

        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          assert value instanceof FileInfo;
          final Component c = super.getListCellRendererComponent(list, value, index, selected, selected);
          final Color bg = UIUtil.getListBackground();
          final Color fg = UIUtil.getListForeground();
          myLabel.setFont(list.getFont());
          myLabel.setForeground(open ? fg : bg);

          myPanel.removeAll();
          myPanel.add(myLabel, BorderLayout.WEST);
          myPanel.add(c, BorderLayout.CENTER);

          // Note: Name=name rendered in cell, Description=path to file, as displayed in bottom panel
          myPanel.getAccessibleContext().setAccessibleName(c.getAccessibleContext().getAccessibleName());
          VirtualFile file = ((FileInfo)value).first;
          String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
          String location = FileUtil.getLocationRelativeToUserHome(presentableUrl);
          myPanel.getAccessibleContext().setAccessibleDescription(location);
          if (!selected && list == mouseMoveSrc && index == mouseMoveListIndex) {
            setBackground(ON_MOUSE_OVER_BG_COLOR);
          }
          return myPanel;
        }

        @Override
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
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
          ApplicationManager.getApplication().invokeLater(() -> updatePathLabel());
        }

        private void updatePathLabel() {
          Object[] values = files.getSelectedValues();
          if (values != null && values.length == 1) {
            VirtualFile file = ((FileInfo)values[0]).first;
            String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
            pathLabel.setText(getTitle2Text(FileUtil.getLocationRelativeToUserHome(presentableUrl)));
          }
          else {
            pathLabel.setText(" ");
          }
        }
      };

      files = new JBList(filesModel);
      if (pinned) {
        new NameFilteringListModel<FileInfo>(files, info -> info.getNameForRendering(), s -> !mySpeedSearch.isPopupActive()
                                                                                         || StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
                                                                                         || mySpeedSearch.getComparator().matchingFragments(mySpeedSearch.getEnteredPrefix(), s) != null, mySpeedSearch);
      }

      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(@NotNull ListSelectionEvent e) {
          if (!files.isSelectionEmpty() && !toolWindows.isSelectionEmpty()) {
            toolWindows.getSelectionModel().clearSelection();
          }
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

      this.add(toolWindows, BorderLayout.WEST);
      if (filesModel.size() > 0) {
        files.setAlignmentY(1f);
        final JScrollPane pane = ScrollPaneFactory.createScrollPane(files, true);
        pane.setPreferredSize(new Dimension(files.getPreferredSize().width, 20 * 20));
        this.add(pane, BorderLayout.EAST);
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
        this.add(separator, BorderLayout.CENTER);
      }
      this.add(descriptions, BorderLayout.SOUTH);

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_SWITCHER).getShortcutSet();
      final int modifiers = getModifiers(shortcutSet);
      final boolean isAlt = (modifiers & Event.ALT_MASK) != 0;
      ALT_KEY = isAlt ? VK_CONTROL : VK_ALT;
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
        .setTitle(title)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setCancelKeyEnabled(false)
        .setCancelCallback(() -> {
          Container popupFocusAncestor = getPopupFocusAncestor();
          if (popupFocusAncestor != null) popupFocusAncestor.setFocusTraversalPolicy(null);
          SWITCHER = null;
          return true;
        }).createPopup();

      if (isPinnedMode()) {
        new DumbAwareAction(null, null, null) {
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
      new DumbAwareAction(null, null, null) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          //suppress all actions to activate a toolwindow : IDEA-71277
        }
      }.registerCustomShortcutSet(TW_SHORTCUT, this, myPopup);

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

    }

    private Container getPopupFocusAncestor() {
      JComponent content = myPopup.getContent();
      return content == null ? null : content.getFocusCycleRootAncestor();
    }

    private static void  addFocusTraversalKeys (Container focusCycleRoot, int focusTraversalType, String keyStroke) {
      Set<AWTKeyStroke> focusTraversalKeySet = focusCycleRoot.getFocusTraversalKeys(focusTraversalType);

      Set<AWTKeyStroke> set = new HashSet<>(focusTraversalKeySet);
      set.add(getKeyStroke(keyStroke));
      focusCycleRoot.setFocusTraversalKeys(focusTraversalType, set);
    }

    @NotNull
    protected VirtualFile[] getFiles(@NotNull Project project) {
      return EditorHistoryManager.getInstance(project).getFiles();
    }

    @NotNull
    private static Map<String, ToolWindow> createShortcuts(@NotNull List<ToolWindow> windows) {
      final Map<String, ToolWindow> keymap = new HashMap<>(windows.size());
      final List<ToolWindow> otherTW = new ArrayList<>();
      for (ToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(((ToolWindowImpl)window).getId());
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
    public void keyTyped(@NotNull KeyEvent e) {
    }

    @Override
    public void keyReleased(@NotNull KeyEvent e) {
      boolean ctrl = e.getKeyCode() == CTRL_KEY;
      boolean enter = e.getKeyCode() == VK_ENTER;
      if (ctrl && isAutoHide() || enter) {
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
            this.remove(separator);
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
          if (twManager instanceof ToolWindowManagerImpl) {
            ToolWindowManagerImpl manager = (ToolWindowManagerImpl)twManager;
            manager.hideToolWindow(((ToolWindowImpl)toolWindow).getId(), false, false);
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

    private static void removeElementAt(@NotNull JList jList, int index) {
      final ListModel model = jList.getModel();
      if (model instanceof DefaultListModel) {
        ((DefaultListModel)model).removeElementAt(index);
      }
      else if (model instanceof NameFilteringListModel) {
        ((NameFilteringListModel)model).remove(index);
      }
      else {
        throw new IllegalArgumentException("Wrong list model " + model.getClass());
      }
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

    private boolean isToolWindowsSelected() {
      return getSelectedList() == toolWindows;
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

    public JBList getSelectedList() {
      return getSelectedList(files);
    }

    @Nullable
    JBList getSelectedList(@Nullable JBList preferable) {
      return files.hasFocus() ? files : toolWindows.hasFocus() ? toolWindows : preferable;
    }

    void navigate(final InputEvent e) {
      final boolean openInNewWindow = e != null && e.isShiftDown() && e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == VK_ENTER;
      final Object[] values = getSelectedList().getSelectedValues();
      final String searchQuery = mySpeedSearch != null ? mySpeedSearch.getEnteredPrefix() : null;
      myPopup.cancel(null);
      if (values.length == 0) {
        tryToOpenFileSearch(e, searchQuery);
      }
      else if (values[0] instanceof ToolWindow) {
        final ToolWindow toolWindow = (ToolWindow)values[0];
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
                boolean oldValue = UISettings.getInstance().getReuseNotModifiedTabs();
                UISettings.getInstance().setReuseNotModifiedTabs(false);
                manager.openFile(file, true, true);
                if (oldValue) {
                  CommandProcessor.getInstance().executeCommand(project, () -> UISettings.getInstance().setReuseNotModifiedTabs(true), "", null);
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
          final DataContext dataContext = new DataContext() {
            @Nullable
            @Override
            public Object getData(@NonNls String dataId) {
              if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
                return fileName;
              }
              return context.getData(dataId);
            }
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

    private class SwitcherSpeedSearch extends SpeedSearchBase<SwitcherPanel> implements PropertyChangeListener {
      private Object[] myElements;

      public SwitcherSpeedSearch() {
        super(SwitcherPanel.this);
        addChangeListener(this);
        setComparator(new SpeedSearchComparator(false, true));
      }

      @Override
      protected void processKeyEvent(@NotNull final KeyEvent e) {
        final int keyCode = e.getKeyCode();
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
        return isFilesSelected()
               ? files.getSelectedIndex()
               : files.getModel().getSize() + toolWindows.getSelectedIndex();
      }

      @Override
      protected Object[] getAllElements() {

        final SwitcherPanel switcher = SwitcherPanel.this;

        ListModel filesModel = switcher.files.getModel();
        final Object[] files = new Object[filesModel.getSize()];
        for (int i = 0; i < files.length; i++) {
          files[i] = filesModel.getElementAt(i);
        }

        ListModel twModel = switcher.toolWindows.getModel();
        final Object[] toolWindows = new Object[twModel.getSize()];
        for (int i = 0; i < toolWindows.length; i++) {
          toolWindows[i] = twModel.getElementAt(i);
        }

        myElements = new Object[files.length + toolWindows.length];
        System.arraycopy(files, 0, myElements, 0, files.length);
        System.arraycopy(toolWindows, 0, myElements, files.length, toolWindows.length);

        return myElements;
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
          if (!toolWindows.isSelectionEmpty()) toolWindows.clearSelection();
          files.clearSelection();
          files.setSelectedValue(element, true);
          files.requestFocusInWindow();
        }
        else {
          if (!files.isSelectionEmpty()) files.clearSelection();
          toolWindows.clearSelection();
          toolWindows.setSelectedValue(element, true);
          toolWindows.requestFocusInWindow();
        }
      }

      @Nullable
      @Override
      protected Object findElement(String s) {
        final List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
        return elements.isEmpty() ? null : elements.get(0).node;
      }

      @Override
      public void propertyChange(@NotNull PropertyChangeEvent evt) {
        if (project.isDisposed()) {
          myPopup.cancel();
          return;
        }
        ((NameFilteringListModel)files.getModel()).refilter();
        ((NameFilteringListModel)toolWindows.getModel()).refilter();
        if (files.getModel().getSize() + toolWindows.getModel().getSize() == 0) {
          toolWindows.getEmptyText().setText("");
          files.getEmptyText().setText("Press 'Enter' to search in Project");
        }
        else {
          files.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
          toolWindows.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
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

    private class SwitcherLayouter extends BorderLayout {
      private Rectangle sBounds;
      private Rectangle tBounds;
      private Rectangle fBounds;
      private Rectangle dBounds;

      @Override
      public void layoutContainer(@NotNull Container target) {
        final JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, files);
        JComponent filesPane = scrollPane != null ? scrollPane : files;
        if (sBounds == null || !target.isShowing()) {
          super.layoutContainer(target);
          sBounds = separator.getBounds();
          tBounds = toolWindows.getBounds();
          fBounds = filesPane.getBounds();
          dBounds = descriptions.getBounds();
        }
        else {
          final int h = target.getHeight();
          final int w = target.getWidth();
          sBounds.height = h - dBounds.height;
          tBounds.height = h - dBounds.height;
          fBounds.height = h - dBounds.height;
          fBounds.width = w - sBounds.width - tBounds.width;
          dBounds.width = w;
          dBounds.y = h - dBounds.height;
          separator.setBounds(sBounds);
          toolWindows.setBounds(tBounds);
          filesPane.setBounds(fBounds);
          descriptions.setBounds(dBounds);
        }
      }
    }
  }

  private static class VirtualFilesRenderer extends ColoredListCellRenderer {
    private final SwitcherPanel mySwitcherPanel;
    boolean open;

    public VirtualFilesRenderer(@NotNull SwitcherPanel switcherPanel) {
      mySwitcherPanel = switcherPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof FileInfo) {
        Project project = mySwitcherPanel.project;
        VirtualFile virtualFile = ((FileInfo)value).getFirst();
        String renderedName = ((FileInfo)value).getNameForRendering();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, project));

        FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
        open = FileEditorManager.getInstance(project).isFileOpen(virtualFile);

        boolean hasProblem = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, hasProblem ? JBColor.red : null, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
        append(renderedName, SimpleTextAttributes.fromTextAttributes(attributes));

        // calc color the same way editor tabs do this, i.e. including EPs
        Color color = EditorTabbedContainer.calcTabColor(project, virtualFile);

        if (!selected && color != null) {
          setBackground(color);
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(mySwitcherPanel, this, false, selected);
      }
    }
  }

  private static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    private final Project myProject;
    private String myNameForRendering;

    public FileInfo(VirtualFile first, EditorWindow second, Project project) {
      super(first, second);
      myProject = project;
    }

    String getNameForRendering() {
      if (myNameForRendering == null) {
        // Recently changed files would also be taken into account (not only open 'visible' files)
        myNameForRendering = EditorTabbedContainer.calcFileName(myProject, first);
      }
      return myNameForRendering;
    }
  }
}
