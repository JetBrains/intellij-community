/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
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
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  private static final Color SEPARATOR_COLOR = BORDER_COLOR.brighter();
  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";
  private static final Color ON_MOUSE_OVER_BG_COLOR = new Color(231, 242, 249);
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
  static {
    TW_KEYMAP.put("Messages",  0);
    TW_KEYMAP.put("Project",   1);
    TW_KEYMAP.put("Commander", 2);
    TW_KEYMAP.put("Find",      3);
    TW_KEYMAP.put("Run",       4);
    TW_KEYMAP.put("Debug",     5);
    TW_KEYMAP.put("TODO",      6);
    TW_KEYMAP.put("Structure", 7);
    TW_KEYMAP.put("Changes",   9);

    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent event) {
        ToolWindow tw;
        if (SWITCHER != null && event instanceof KeyEvent) {
          final KeyEvent keyEvent = (KeyEvent)event;
          if (event.getID() == KEY_RELEASED && keyEvent.getKeyCode() == CTRL_KEY) {
           SwingUtilities.invokeLater(CHECKER);
          } else if (event.getID() == KEY_PRESSED && (tw = SWITCHER.twShortcuts.get(String.valueOf((char)keyEvent.getKeyCode()))) != null) {
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
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    boolean selectFirstItem = false;
    if (SWITCHER == null) {
      synchronized (Switcher.class) {
        if (SWITCHER == null) {
          SWITCHER = new SwitcherPanel(project);
          FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
          selectFirstItem = !FileEditorManagerEx.getInstanceEx(project).hasOpenedFile();
        }
      }
    }

    if (e.getInputEvent().isShiftDown()) {
      SWITCHER.goBack();
    } else {
      if (selectFirstItem) {
        SWITCHER.files.setSelectedIndex(0);
      } else {
        SWITCHER.goForward();
      }
    }
  }

  private class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    public final int MAX_FILES = UISettings.getInstance().EDITOR_TAB_LIMIT;
    final JBPopup myPopup;
    final Map<ToolWindow, String> ids = new HashMap<ToolWindow, String>();
    final JList toolWindows;
    final JList files;
    final JPanel separator;
    final ToolWindowManager twManager;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel descriptions;
    final Project project;
    final Map<String, ToolWindow> twShortcuts;

    @SuppressWarnings({"ManualArrayToCollectionCopy"})
    SwitcherPanel(Project project) {
      super(new BorderLayout(0, 0));
      this.project = project;
      setFocusable(true);
      addKeyListener(this);
      setBorder(new EmptyBorder(0, 0, 0, 0));
      setBackground(Color.WHITE);
      pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont((float)10));

      descriptions = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          g.setColor(BORDER_COLOR);
          g.drawLine(0, 0, getWidth(), 0);
        }
      };

      descriptions.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
      descriptions.add(pathLabel);
      twManager = ToolWindowManager.getInstance(project);
      final DefaultListModel twModel = new DefaultListModel();
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
      toolWindows = new JBList(twModel);
      toolWindows.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      toolWindows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      toolWindows.setCellRenderer(new SwitcherToolWindowsListRenderer(ids, map) {
        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);
          if (selected){
            return renderer;
          }
          final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ?  ON_MOUSE_OVER_BG_COLOR : Color.WHITE;
          UIUtil.changeBackGround(renderer, bgColor);
          return renderer;
        }
      });
      toolWindows.addKeyListener(this);
      toolWindows.addMouseListener(this);
      toolWindows.addMouseMotionListener(this);
      toolWindows.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!toolWindows.getSelectionModel().isSelectionEmpty()) {
            files.getSelectionModel().clearSelection();
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

        @Override
        public Dimension getMaximumSize() {
          final Dimension max = super.getMaximumSize();
          return new Dimension(5, max.height);
        }
      };
      separator.setBackground(Color.WHITE);

      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      final ArrayList<FileInfo> filesData = new ArrayList<FileInfo>();
      final ArrayList<FileInfo> editors = new ArrayList<FileInfo>();
      for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
        editors.add(new FileInfo(pair.first, pair.second));
      }
      if (editors.size() < 2) {
        final VirtualFile[] recentFiles = ArrayUtil.reverseArray(EditorHistoryManager.getInstance(project).getFiles());
        final int len = Math.min(toolWindows.getModel().getSize(), Math.max(editors.size(), recentFiles.length));
        for (int i = 0; i < len; i++) {
          filesData.add(new FileInfo(recentFiles[i], null));
        }
        if (editors.size() == 1) {
          filesData.add(0, editors.get(0));
        }
      } else {
        for (int i = 0; i < Math.min(MAX_FILES, editors.size()); i++) {
          filesData.add(editors.get(i));
        }
      }

      final DefaultListModel filesModel = new DefaultListModel();
      for (FileInfo editor : filesData) {
        filesModel.addElement(editor);
      }

      files = new JBList(filesModel);
      files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      files.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      files.setCellRenderer(new VirtualFilesRenderer(project) {
        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);
          if (selected){
            return renderer;
          }
          final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ?  ON_MOUSE_OVER_BG_COLOR : Color.WHITE;
          UIUtil.changeBackGround(renderer, bgColor);
          return renderer;
        }
      });
      files.addKeyListener(this);
      files.addMouseListener(this);
      files.addMouseMotionListener(this);
      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!files.getSelectionModel().isSelectionEmpty()) {
            toolWindows.getSelectionModel().clearSelection();
          }
        }
      });

      this.add(toolWindows, BorderLayout.WEST);
      if (filesModel.size() > 0) {
        files.setAlignmentY(1f);
        this.add(files, BorderLayout.EAST);
        this.add(separator, BorderLayout.CENTER);
      }
      this.add(descriptions, BorderLayout.SOUTH);

      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
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
              pathLabel.setText(getTitle2Text(parent.getPresentableUrl()));
            } else {
              pathLabel.setText(" ");
            }
          } else {
            pathLabel.setText(" ");
          }
        }
      });

      final int modifiers = getModifiers(Switcher.this.getShortcutSet());
      final boolean isAlt = (modifiers & Event.ALT_MASK) != 0;
      ALT_KEY = isAlt ? VK_CONTROL : VK_ALT;
      CTRL_KEY = isAlt ? VK_ALT : VK_CONTROL;

      final IdeFrameImpl ideFrame = WindowManagerEx.getInstanceEx().getFrame(project);
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
          .setResizable(false)
          .setModalContext(false)
          .setFocusable(true)
          .setRequestFocus(true)
          .setTitle(SWITCHER_TITLE)
          .setMovable(false)
          .setCancelCallback(new Computable<Boolean>() {
          public Boolean compute() {
            SWITCHER = null;
            return true;
          }
        }).createPopup();
      Component comp = null;
      final EditorWindow result = FileEditorManagerEx.getInstanceEx(project).getActiveWindow().getResult();
      if (result != null) {
        comp = result.getOwner();
      }
      if (comp == null) {
        comp = ideFrame.getContentPane();
      }

      myPopup.showInCenterOf(comp);
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

    private int getModifiers(ShortcutSet shortcutSet) {
      if (shortcutSet == null
          || shortcutSet.getShortcuts().length == 0
          || !(shortcutSet.getShortcuts()[0] instanceof KeyboardShortcut)) return Event.CTRL_MASK;
      return ((KeyboardShortcut)shortcutSet.getShortcuts()[0]).getFirstKeyStroke().getModifiers();
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
      if (e.getKeyCode() == CTRL_KEY || e.getKeyCode() == VK_ENTER) {
        navigate();
      } else
      if (e.getKeyCode() == VK_LEFT) {
        goLeft();
      } else if (e.getKeyCode() == VK_RIGHT) {
        goRight();
      }
    }

    public void keyPressed(KeyEvent e) {
      switch (e.getKeyCode()) {
        case VK_UP:
          goBack();
          break;
        case VK_DOWN:
          goForward();
          break;
        case VK_ESCAPE:
          cancel();
          break;
        case VK_DELETE:
        case VK_BACK_SPACE: // Mac users
          closeTabOrToolWindow();
          break;
      }
      if (e.getKeyCode() == ALT_KEY) {
        if (isFilesSelected()) {
          goLeft();
        } else {
          goRight();
        }
      }
    }

    private void closeTabOrToolWindow() {
      final Object value = getSelectedList().getSelectedValue();
      if (value instanceof FileInfo) {
        final FileInfo info = (FileInfo)value;
        final VirtualFile virtualFile = info.first;
        final FileEditorManagerImpl editorManager = ((FileEditorManagerImpl)FileEditorManager.getInstance(project));
        final JList jList = getSelectedList();
        final EditorWindow wnd = findAppropriateWindow(info);
        if (wnd == null) {
          editorManager.closeFile(virtualFile, false);
        } else {
          editorManager.closeFile(virtualFile, wnd, false);
        }
        final int selectedIndex = jList.getSelectedIndex();
        final IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
        new Thread() { //TODO[kb]: think how to do it better. Need to remove all hacks & work correctly with activateEditorComponentImpl method
          @Override
          public void run() {
            try {
              sleep(300);
            }
            catch (InterruptedException e) {//
            }
            focusManager.requestFocus(SwitcherPanel.this, true);
          }
        }.start();
        if (jList.getModel().getSize() == 1) {
          goLeft();
          ((DefaultListModel)jList.getModel()).removeElementAt(selectedIndex);
          this.remove(jList);
          this.remove(separator);
          final Dimension size = toolWindows.getSize();
          myPopup.setSize(new Dimension(size.width, myPopup.getSize().height));
        } else {
          goForward();
          ((DefaultListModel)jList.getModel()).removeElementAt(selectedIndex);
          jList.setSize(jList.getPreferredSize());
        }
        pack();
      } else if (value instanceof ToolWindow) {
        final ToolWindow toolWindow = (ToolWindow)value;
        if (twManager instanceof ToolWindowManagerImpl) {
          ToolWindowManagerImpl manager = (ToolWindowManagerImpl)twManager;
          manager.hideToolWindow(ids.get(toolWindow), false, false);
        } else {
          toolWindow.hide(null);
        }
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
      if (isFilesSelected() || !isFilesVisible()) {
        cancel();
      }
      else {
        if (files.getModel().getSize() > 0) {
          files.setSelectedIndex(Math.min(toolWindows.getSelectedIndex(), files.getModel().getSize() - 1));
          toolWindows.getSelectionModel().clearSelection();
        }
      }
    }

    private void cancel() {
      myPopup.cancel();
    }

    private void goLeft() {
      if (isToolWindowsSelected()) {
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
    }

    public JList getSelectedList() {
      if (toolWindows.isSelectionEmpty() && files.isSelectionEmpty()) {
        if (files.getModel().getSize() > 1) {
          files.setSelectedIndex(0);
          return files;
        }
        else {
          toolWindows.setSelectedIndex(0);
          return toolWindows;
        }
      }
      else {
        return toolWindows.isSelectionEmpty() ? files : toolWindows;
      }
    }

    void navigate() {
      myPopup.closeOk(null);
      final Object value = getSelectedList().getSelectedValue();
      if (value instanceof ToolWindow) {
        ((ToolWindow)value).activate(null, true, true);
      }
      else if (value instanceof FileInfo) {
        final FileInfo info = (FileInfo)value;
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
            if (info.second != null) {
              EditorWindow wnd = findAppropriateWindow(info);
              if (wnd != null) {
                manager.openFileImpl2(wnd, info.first, true);
                manager.addSelectionRecord(info.first, wnd);
              }
            } else {
              manager.openFile(info.first, true);
            }
          }
        });
      }
    }

    @Nullable
    private EditorWindow findAppropriateWindow(FileInfo info) {
      if (info.second == null) return null;
      final EditorWindow[] windows = info.second.getOwner().getWindows();
      return ArrayUtil.contains(info.second, windows) ? info.second : windows.length > 0 ? windows[0] : null;
    }

    public void mouseClicked(MouseEvent e) {
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
  }

  private static class VirtualFilesRenderer extends ColoredListCellRenderer {
    private final Project myProject;

    public VirtualFilesRenderer(Project project) {
      myProject = project;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof FileInfo) {
        final VirtualFile virtualFile = ((FileInfo)value).first;
        final String name = virtualFile.getPresentableName();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject));

        final FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        final TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));
      }
    }
  }

  private static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    public FileInfo(VirtualFile first, EditorWindow second) {
      super(first, second);
    }
  }
}
