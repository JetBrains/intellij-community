/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

import static java.awt.event.KeyEvent.*;

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
  public static final Runnable CHECKER = new Runnable() {
    @Override
    public void run() {
      synchronized (Switcher.class) {
        if (SWITCHER != null) {
          SWITCHER.navigate();
        }
      }
    }
  };
  private static final Map<String, Integer> TW_KEYMAP = new HashMap<String, Integer>();
  private static final CustomShortcutSet TW_SHORTCUT;

  static {
    TW_KEYMAP.put("Messages",  0);
    TW_KEYMAP.put("Project",   1);
    TW_KEYMAP.put("Favorites", 2);
    TW_KEYMAP.put("Find",      3);
    TW_KEYMAP.put("Run",       4);
    TW_KEYMAP.put("Debug",     5);
    TW_KEYMAP.put("TODO",      6);
    TW_KEYMAP.put("Structure", 7);
    TW_KEYMAP.put("Hierarchy", 8);
    TW_KEYMAP.put("Changes",   9);

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    for (char ch = '0'; ch <= '9'; ch++) {
      shortcuts.add(CustomShortcutSet.fromString("control " + ch).getShortcuts()[0]);
    }
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      shortcuts.add(CustomShortcutSet.fromString("control " + ch).getShortcuts()[0]);
    }
    TW_SHORTCUT = new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()]));


      IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
        @Override
        public boolean dispatch(AWTEvent event) {
          ToolWindow tw;
          if (SWITCHER != null && event instanceof KeyEvent && !SWITCHER.isPinnedMode()) {
            final KeyEvent keyEvent = (KeyEvent)event;
            if (event.getID() == KEY_RELEASED && keyEvent.getKeyCode() == CTRL_KEY) {
              SwingUtilities.invokeLater(CHECKER);
            }
            else if (event.getID() == KEY_PRESSED
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

  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;

      synchronized (Switcher.class) {
        if (SWITCHER != null && SWITCHER.isPinnedMode()) {
          SWITCHER.cancel();
          SWITCHER = null;
        }
        if (SWITCHER == null) {
          SWITCHER = createAndShowSwitcher(project, SWITCHER_TITLE, false);
          FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
        }
      }


    assert SWITCHER != null;
    if (!SWITCHER.isPinnedMode()) {
      if (e.getInputEvent().isShiftDown()) {
        SWITCHER.goBack();
      } else {
        if (!FileEditorManagerEx.getInstanceEx(project).hasOpenedFile()) {
          SWITCHER.files.setSelectedIndex(0);
        } else {
          SWITCHER.goForward();
        }
      }
    }
  }

  public static SwitcherPanel createAndShowSwitcher(Project project, String title, boolean pinned) {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.cancel();
      }
      SWITCHER = new SwitcherPanel(project, title, pinned);
      return SWITCHER;
    }
  }

  public static class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    private final int MAX_FILES_IN_SWITCHER;
    final JBPopup myPopup;
    final Map<ToolWindow, String> ids = new HashMap<ToolWindow, String>();
    final MyList toolWindows;
    final MyList files;
    final JPanel separator;
    final ToolWindowManager twManager;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel descriptions;
    final Project project;
    private final boolean myPinned;
    final Map<String, ToolWindow> twShortcuts;
    final Alarm myAlarm;
    final SwitcherSpeedSearch mySpeedSearch;
    final ClickListener myClickListener = new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (myPinned && (e.isControlDown() || e.isMetaDown() || e.isShiftDown())) return false;
        final Object source = e.getSource();
        if (source instanceof JList) {
          JList jList = (JList)source;
          if (jList.getSelectedIndex() == -1 && jList.getAnchorSelectionIndex() != -1) {
            jList.setSelectedIndex(jList.getAnchorSelectionIndex());
          }
          if (jList.getSelectedIndex() != -1) {
            navigate();
          }
        }
        return true;
      }
    };

    @SuppressWarnings({"ManualArrayToCollectionCopy", "ConstantConditions"})
    SwitcherPanel(final Project project, String title, boolean pinned) {
      setLayout(new SwitcherLayouter());
      this.project = project;
      MAX_FILES_IN_SWITCHER = pinned ? UISettings.getInstance().RECENT_FILES_LIMIT : 30;
      myPinned = pinned;
      mySpeedSearch =  pinned ? new SwitcherSpeedSearch() : null;

      setFocusable(true);
      addKeyListener(this);
      setBorder(new EmptyBorder(0, 0, 0, 0));
      setBackground(JBColor.background());
      pathLabel.setHorizontalAlignment(SwingConstants.LEFT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont(Math.max(10f, font.getSize() - 4f)));

      descriptions = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          g.setColor(UIUtil.isUnderDarcula() ? SEPARATOR_COLOR : BORDER_COLOR);
          g.drawLine(0, 0, getWidth(), 0);
        }
      };

      descriptions.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
      descriptions.add(pathLabel, BorderLayout.CENTER);
      twManager = ToolWindowManager.getInstance(project);
      DefaultListModel twModel = new DefaultListModel();
      for (String id : twManager.getToolWindowIds()) {
        final ToolWindow tw = twManager.getToolWindow(id);
        if (tw.isAvailable()) {
          ids.put(tw, id);
        }
      }

      final ArrayList<ToolWindow> windows = new ArrayList<ToolWindow>(ids.keySet());
      twShortcuts = createShortcuts(windows);
      final Map<ToolWindow, String> map = ContainerUtil.reverseMap(twShortcuts);
      Collections.sort(windows, new Comparator<ToolWindow>() {
        @Override
        public int compare(ToolWindow o1, ToolWindow o2) {
          return map.get(o1).compareTo(map.get(o2));
        }
      });
      for (ToolWindow window : windows) {
        twModel.addElement(window);
      }

      toolWindows = new MyList(twModel);
      if (pinned) {
        new NameFilteringListModel<ToolWindow>(toolWindows, new Function<ToolWindow, String>() {
          @Override
          public String fun(ToolWindow window) {
            return ids.get(window);
          }
        }, new Condition<String>() {
          @Override
          public boolean value(String s) {
            return !mySpeedSearch.isPopupActive()
                   || StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
                   || mySpeedSearch.getComparator().matchingFragments(mySpeedSearch.getEnteredPrefix(), s) != null;
          }
        }, mySpeedSearch);
      }

      toolWindows.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      toolWindows.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      toolWindows.setCellRenderer(new SwitcherToolWindowsListRenderer(mySpeedSearch, ids, map, myPinned) {
        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);
          if (selected) {
            return renderer;
          }
          final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ? ON_MOUSE_OVER_BG_COLOR : list.getBackground();
          UIUtil.changeBackGround(renderer, bgColor);
          return renderer;
        }
      });
      toolWindows.addKeyListener(this);
      toolWindows.addMouseListener(this);
      toolWindows.addMouseMotionListener(this);
      myClickListener.installOn(toolWindows);
      toolWindows.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!toolWindows.isSelectionEmpty() && !files.isSelectionEmpty()) {
            files.clearSelection();
          }
        }
      });

      separator = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          g.setColor(SEPARATOR_COLOR);
          g.drawLine(0, 0, 0, getHeight());
        }
      };
      separator.setBackground(toolWindows.getBackground());

      int selectionIndex = -1;
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      final ArrayList<FileInfo> filesData = new ArrayList<FileInfo>();
      final ArrayList<FileInfo> editors = new ArrayList<FileInfo>();
      if (!pinned) {
        for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
          editors.add(new FileInfo(pair.first, pair.second));
        }
      }
      if (editors.size() < 2 || isPinnedMode()) {
        if (isPinnedMode() && editors.size() > 1) {
          filesData.addAll(editors);
        }
        final VirtualFile[] recentFiles = ArrayUtil.reverseArray(EditorHistoryManager.getInstance(project).getFiles());
        final int maxFiles = Math.max(editors.size(), recentFiles.length);
        final int len = isPinnedMode() ? recentFiles.length : Math.min(toolWindows.getModel().getSize(), maxFiles);
        boolean firstRecentMarked = false;
        for (int i = 0; i < len; i++) {
          final FileInfo info = new FileInfo(recentFiles[i], null);
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
              firstRecentMarked = true;
              selectionIndex = filesData.size() - 1;
            }
          }
        }
        if (editors.size() == 1) selectionIndex++;
        if (editors.size() == 1 && (filesData.isEmpty() || !editors.get(0).getFirst().equals(filesData.get(0).getFirst()))) {
          filesData.add(0, editors.get(0));
        }
      } else {
        for (int i = 0; i < Math.min(MAX_FILES_IN_SWITCHER, editors.size()); i++) {
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
          protected void paintComponent(Graphics g) {
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

        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final Component c = super.getListCellRendererComponent(list, value, index, selected, hasFocus);
          final Color bg = UIUtil.getListBackground();
          final Color fg = UIUtil.getListForeground();
          myLabel.setFont(list.getFont());
          myLabel.setForeground(open ? fg : bg);

          myPanel.removeAll();
          myPanel.add(myLabel, BorderLayout.WEST);
          myPanel.add(c, BorderLayout.CENTER);
          return myPanel;
        }
      };

      final ListSelectionListener filesSelectionListener = new ListSelectionListener() {
        private String getTitle2Text(String fullText) {
          int labelWidth = pathLabel.getWidth();
          if (fullText == null || fullText.length() == 0) return " ";
          while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
            int sep = fullText.indexOf(File.separatorChar, 4);
            if (sep < 0) return fullText;
            fullText = "..." + fullText.substring(sep);
          }

          return fullText;
        }

        public void valueChanged(final ListSelectionEvent e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              updatePathLabel();
            }
          });
        }

        private void updatePathLabel() {
          final Object[] values = files.getSelectedValues();
          if (values != null && values.length == 1) {
            final VirtualFile parent = ((FileInfo)values[0]).first.getParent();
            if (parent != null) {
              pathLabel.setText(getTitle2Text(FileUtil.getLocationRelativeToUserHome(parent.getPresentableUrl())));
            } else {
              pathLabel.setText(" ");
            }
          } else {
            pathLabel.setText(" ");
          }
        }
      };

      files = new MyList(filesModel);
      if (pinned) {
        new NameFilteringListModel<FileInfo>(files, new Function<FileInfo, String>() {
          @Override
          public String fun(FileInfo info) {
            final VirtualFile file = info.getFirst();
            return file instanceof VirtualFilePathWrapper ? ((VirtualFilePathWrapper)file).getPresentablePath() : file.getName();
          }
        }, new Condition<String>() {
          @Override
          public boolean value(String s) {
            return !mySpeedSearch.isPopupActive()
                   || StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
                   || mySpeedSearch.getComparator().matchingFragments(mySpeedSearch.getEnteredPrefix(), s) != null;
          }
        }, mySpeedSearch);
      }

      files.setSelectionMode(pinned ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!files.isSelectionEmpty() && !toolWindows.isSelectionEmpty()) {
            toolWindows.getSelectionModel().clearSelection();
          }
        }
      });

      files.getSelectionModel().addListSelectionListener(filesSelectionListener);

      files.setCellRenderer(filesRenderer);
      files.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      files.addKeyListener(this);
      files.addMouseListener(this);
      files.addMouseMotionListener(this);
      myClickListener.installOn(files);

      this.add(toolWindows, BorderLayout.WEST);
      if (filesModel.size() > 0) {
        files.setAlignmentY(1f);
        if (files.getModel().getSize() > 20) {
          final JScrollPane pane = ScrollPaneFactory.createScrollPane(files, true);
          pane.setPreferredSize(new Dimension(files.getPreferredSize().width + 10, 20 * 20));
          pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
          this.add(pane, BorderLayout.EAST);
        } else {
          this.add(files, BorderLayout.EAST);
        }
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
        this.add(separator, BorderLayout.CENTER);
      }
      this.add(descriptions, BorderLayout.SOUTH);

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction("Switcher").getShortcutSet();
      final int modifiers = getModifiers(shortcutSet);
      final boolean isAlt = (modifiers & Event.ALT_MASK) != 0;
      ALT_KEY = isAlt ? VK_CONTROL : VK_ALT;
      CTRL_KEY = isAlt ? VK_ALT : VK_CONTROL;

      final IdeFrameImpl ideFrame = WindowManagerEx.getInstanceEx().getFrame(project);
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
        .setResizable(pinned)
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setTitle(title)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setCancelKeyEnabled(false)
        .setCancelCallback(new Computable<Boolean>() {
          public Boolean compute() {
            SWITCHER = null;
            return true;
          }
        }).createPopup();

      if (isPinnedMode()) {
        new AnAction(null ,null ,null) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            changeSelection();
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), this, myPopup);

        new AnAction(null, null, null) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            //suppress all actions to activate a toolwindow : IDEA-71277
          }
        }.registerCustomShortcutSet(TW_SHORTCUT, this, myPopup);
        new AnAction(null, null, null) {

          @Override
          public void actionPerformed(AnActionEvent e) {
            if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
              mySpeedSearch.hidePopup();
            } else {
              myPopup.cancel();
            }
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), this, myPopup);
      }

      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
      myPopup.showInCenterOf(window);
    }


    private Map<String, ToolWindow> createShortcuts(List<ToolWindow> windows) {
      final Map<String, ToolWindow> keymap = new HashMap<String, ToolWindow>(windows.size());
      final List<ToolWindow> pluginToolWindows = new ArrayList<ToolWindow>();
      for (ToolWindow window : windows) {
        final Integer index = TW_KEYMAP.get(ids.get(window));
        if (index != null) {
          keymap.put(Integer.toString(index, index + 1).toUpperCase(), window);
        } else {
          pluginToolWindows.add(window);
        }
      }
      final Iterator<ToolWindow> iterator = pluginToolWindows.iterator();
      int i = 0;
      while (iterator.hasNext()) {
        while (keymap.get(Integer.toString(i, i + 1).toUpperCase()) != null) {
          i++;
        }
        keymap.put(Integer.toString(i, i + 1).toUpperCase(), iterator.next());
        i++;
      }
      return keymap;
    }

    private static int getModifiers(ShortcutSet shortcutSet) {
      if (shortcutSet == null
          || shortcutSet.getShortcuts().length == 0
          || !(shortcutSet.getShortcuts()[0] instanceof KeyboardShortcut)) return Event.CTRL_MASK;
      return ((KeyboardShortcut)shortcutSet.getShortcuts()[0]).getFirstKeyStroke().getModifiers();
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
      if ((e.getKeyCode() == CTRL_KEY && isAutoHide())
          || e.getKeyCode() == VK_ENTER) {
        navigate();
      } else
      if (e.getKeyCode() == VK_LEFT) {
        goLeft();
      } else if (e.getKeyCode() == VK_RIGHT) {
        goRight();
      }
    }

    KeyEvent lastEvent;
    public void keyPressed(KeyEvent e) {
      if ((mySpeedSearch != null && mySpeedSearch.isPopupActive()) || lastEvent == e) return;
      lastEvent = e;
      switch (e.getKeyCode()) {
        case VK_UP:
          if (!isPinnedMode()) {
            goBack();
          } else {
            getSelectedList().processKeyEvent(e);
          }
          break;
        case VK_DOWN:
          if (!isPinnedMode()) {
            goForward();
          } else {
            getSelectedList().processKeyEvent(e);
          }
          break;
        case VK_ESCAPE:
          cancel();
          break;
        case VK_END:
          ListScrollingUtil.moveEnd(getSelectedList());
          break;
        case VK_PAGE_DOWN:
          ListScrollingUtil.movePageDown(getSelectedList());
          break;
        case VK_HOME:
          ListScrollingUtil.moveHome(getSelectedList());
          break;
        case VK_PAGE_UP:
          ListScrollingUtil.movePageUp(getSelectedList());
          break;
        case VK_DELETE:
        case VK_BACK_SPACE: // Mac users
        case VK_Q:
          closeTabOrToolWindow();
          break;
      }
      if (e.getKeyCode() == ALT_KEY) {
        changeSelection();
      }
    }

    private void changeSelection() {
      if (isFilesSelected()) {
        goLeft();
      } else {
        goRight();
      }
    }

    private void closeTabOrToolWindow() {
      final int[] selected = getSelectedList().getSelectedIndices();
      Arrays.sort(selected);
      int selectedIndex = 0;
      for (int i = selected.length - 1; i>=0; i--) {
        selectedIndex = selected[i];
        Object value = getSelectedList().getModel().getElementAt(selectedIndex);
        if (value instanceof FileInfo) {
          final FileInfo info = (FileInfo)value;
          final VirtualFile virtualFile = info.first;
          final FileEditorManagerImpl editorManager = ((FileEditorManagerImpl)FileEditorManager.getInstance(project));
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
          myAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
              focusManager.requestFocus(SwitcherPanel.this, true);
            }
          }, 300);
          if (jList.getModel().getSize() == 1) {
            goLeft();
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
            manager.hideToolWindow(ids.get(toolWindow), false, false);
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

    private static void removeElementAt(JList jList, int index) {
      final ListModel model = jList.getModel();
      if (model instanceof DefaultListModel) {
       ((DefaultListModel)model).removeElementAt(index);
      } else if (model instanceof NameFilteringListModel) {
        ((NameFilteringListModel)model).remove(index);
      } else {
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
      } while (container != rootPane);
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

    private void goRight() {
      if ((isFilesSelected() || !isFilesVisible()) && isAutoHide()) {
        cancel();
      }
      else {
        if (files.getModel().getSize() > 0) {
          final int index = Math.min(toolWindows.getSelectedIndex(), files.getModel().getSize() - 1);
          files.setSelectedIndex(index);
          files.ensureIndexIsVisible(index);
          toolWindows.getSelectionModel().clearSelection();
        }
      }
    }

    private void cancel() {
      myPopup.cancel();
    }

    private void goLeft() {
      if (isToolWindowsSelected() && isAutoHide()) {
        cancel();
      }
      else {
        if (toolWindows.getModel().getSize() > 0) {
          toolWindows.setSelectedIndex(Math.min(files.getSelectedIndex(), toolWindows.getModel().getSize() - 1));
          files.getSelectionModel().clearSelection();
        }
      }
    }

    public void goForward() {
      JList list = getSelectedList();
      int index = list.getSelectedIndex() + 1;
      if (index >= list.getModel().getSize()) {
        index = 0;
        if (isFilesVisible()) {
          list = isFilesSelected() ? toolWindows : files;
        }
      }
      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
    }

    public void goBack() {
      JList list = getSelectedList();
      int index = list.getSelectedIndex() - 1;
      if (index < 0) {
        if (isFilesVisible()) {
          list = isFilesSelected() ? toolWindows : files;
        }
        index = list.getModel().getSize() - 1;
      }
      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
    }

    public MyList getSelectedList() {
      return getSelectedList(files);
    }

    MyList getSelectedList(MyList preferable) {
      if (toolWindows.isSelectionEmpty() && files.isSelectionEmpty()) {
        if (preferable != null && preferable.getModel().getSize() > 0) {
          preferable.setSelectedIndex(0);
          return preferable;
        } else

        if (files.getModel().getSize() > 0) {
          files.setSelectedIndex(0);
          return files;
        } else {
          toolWindows.setSelectedIndex(0);
          return toolWindows;
        }
      }
      else {
        return toolWindows.isSelectionEmpty() ? files : toolWindows;
      }
    }

    void navigate() {
      final Object[] values = getSelectedList().getSelectedValues();
      myPopup.closeOk(null);
      if (values.length > 0 && values[0] instanceof ToolWindow) {
        ((ToolWindow)values[0]).activate(null, true, true);
      } else{
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
            for (Object value : values) {
              if (value instanceof FileInfo) {
                final FileInfo info = (FileInfo)value;

                if (info.second != null) {
                  EditorWindow wnd = findAppropriateWindow(info);
                  if (wnd != null) {
                    manager.openFileImpl2(wnd, info.first, true);
                    manager.addSelectionRecord(info.first, wnd);
                  }
                } else {
                  manager.openFile(info.first, true, true);
                }
              }
            }
          }
        });
      }
    }

    @Nullable
    private static EditorWindow findAppropriateWindow(FileInfo info) {
      if (info.second == null) return null;
      final EditorWindow[] windows = info.second.getOwner().getWindows();
      return ArrayUtil.contains(info.second, windows) ? info.second : windows.length > 0 ? windows[0] : null;
    }

    public void mouseClicked(MouseEvent e) {
    }

    private boolean mouseMovedFirstTime = true;
    private JList mouseMoveSrc = null;
    private int mouseMoveListIndex = -1;
    public void mouseMoved(MouseEvent e) {
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

    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {
      mouseMoveSrc = null;
      mouseMoveListIndex = -1;
      repaintLists();
    }
    public void mouseDragged(MouseEvent e) {}

    private class SwitcherSpeedSearch extends SpeedSearchBase<SwitcherPanel> implements PropertyChangeListener {
      private Object[] myElements;

      public SwitcherSpeedSearch() {
        super(SwitcherPanel.this);
        addChangeListener(this);
        setComparator(new SpeedSearchComparator(false, true));
      }

      @Override
      protected void processKeyEvent(final KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (keyCode == VK_LEFT || keyCode == VK_RIGHT) {
          return;
        }
        if (keyCode == VK_ENTER && files.getModel().getSize() + toolWindows.getModel().getSize() == 0) {
          AnAction gotoAction = ActionManager.getInstance().getAction("GotoClass");
          if (gotoAction == null) {
            gotoAction = ActionManager.getInstance().getAction("GotoFile");
          }
          if (gotoAction != null) {
            final String search = mySpeedSearch.getEnteredPrefix();
            myPopup.cancel();
            final AnAction action = gotoAction;
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {

                DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
                  @Override
                  public void consume(final DataContext context) {
                    final DataContext dataContext = new DataContext() {
                      @Nullable
                      @Override
                      public Object getData(@NonNls String dataId) {
                        if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
                          return search;
                        }
                        return context.getData(dataId);
                      }
                    };
                    final AnActionEvent event =
                      new AnActionEvent(e, dataContext, ActionPlaces.EDITOR_POPUP, new PresentationFactory().getPresentation(action),
                                        ActionManager.getInstance(), 0);
                    action.actionPerformed(event);
                  }
                });
              }
            });
            return;
          }
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
          return ids.get(element);
        } else if (element instanceof FileInfo) {
          final VirtualFile file = ((FileInfo)element).getFirst();
          return file instanceof VirtualFilePathWrapper ? ((VirtualFilePathWrapper)file).getPresentablePath() : file.getName();
        }
        return "";
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        if (element instanceof FileInfo) {
          if (!toolWindows.isSelectionEmpty()) toolWindows.clearSelection();
          files.setSelectedValue(element, false);
        } else {
          if (!files.isSelectionEmpty()) files.clearSelection();
          toolWindows.setSelectedValue(element, false);
        }
      }

      @Nullable
      @Override
      protected Object findElement(String s) {
        final List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
        return elements.isEmpty() ? null : elements.get(0).node;
      }

      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        final MyList list = getSelectedList();
        final Object value = list.getSelectedValue();
        if (project.isDisposed()) {
          myPopup.cancel();
          return;
        }
        ((NameFilteringListModel)files.getModel()).refilter();
        ((NameFilteringListModel)toolWindows.getModel()).refilter();
        if (files.getModel().getSize() + toolWindows.getModel().getSize() == 0) {
          toolWindows.getEmptyText().setText("");
          files.getEmptyText().setText("Press 'Enter' to search in Project");
        } else {
          files.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
          toolWindows.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
        }
        files.repaint();
        toolWindows.repaint();
        getSelectedList(list).setSelectedValue(value, true);
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
      public void layoutContainer(Container target) {
        final JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, files);
        JComponent filesPane = scrollPane != null ? scrollPane : files;
        if (sBounds == null) {
          super.layoutContainer(target);
          sBounds = separator.getBounds();
          tBounds = toolWindows.getBounds();
          fBounds = filesPane.getBounds();
          dBounds = descriptions.getBounds();
        } else {
          final int h = target.getHeight();
          final int w = target.getWidth();
          sBounds.height = h - dBounds.height;
          tBounds.height = h - dBounds.height;
          fBounds.height = h - dBounds.height;
          fBounds.width =  w - sBounds.width - tBounds.width;
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

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof FileInfo) {
        Project project = mySwitcherPanel.project;
        VirtualFile virtualFile = ((FileInfo)value).getFirst();
        String name = virtualFile instanceof VirtualFilePathWrapper
                      ? ((VirtualFilePathWrapper)virtualFile).getPresentablePath()
                      : UISettings.getInstance().SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES
                        ? UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, virtualFile)
                        : virtualFile.getName();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, project));

        FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
        open = FileEditorManager.getInstance(project).isFileOpen(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null , null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));

        // calc color the same way editor tabs do this, i.e. including extensions
        Color color = EditorTabbedContainer.calcTabColor(project, virtualFile);

        if (!selected &&  color != null) {
          setBackground(color);
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(mySwitcherPanel, this, false, selected);
      }
    }
  }

  private static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    public FileInfo(VirtualFile first, EditorWindow second) {
      super(first, second);
    }
  }

  private static class MyList extends JBList {
    public MyList(DefaultListModel model) {
      super(model);
    }

    @Override
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }
}
