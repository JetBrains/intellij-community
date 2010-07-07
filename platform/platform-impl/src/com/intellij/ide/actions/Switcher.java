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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.FocusManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class Switcher extends AnAction implements DumbAware {
  private static volatile SwitcherPanel SWITCHER = null;
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  private static final Color SEPARATOR_COLOR = BORDER_COLOR.brighter();
  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";
  private static final Color ON_MOUSE_OVER_BG_COLOR = new Color(231, 242, 249);

  private static final KeyListener performanceProblemsSolver = new KeyAdapter() { //IDEA-24436
    @Override
    public void keyReleased(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
        synchronized (Switcher.class) {
          if (SWITCHER != null) {
            SWITCHER.navigate();
          }
        }
      }
    }
  };

  private static Component focusComponent = null;
  @NonNls private static final String SWITCHER_TITLE = "Switcher";

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    if (SWITCHER == null) {
      focusComponent = FocusManager.getCurrentManager().getFocusOwner();
      if (focusComponent != null) {
        focusComponent.addKeyListener(performanceProblemsSolver);
      }
      SWITCHER = new SwitcherPanel(project);
      FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
    }

    if (e.getInputEvent().isShiftDown()) {
      SWITCHER.goBack();
    } else {
      SWITCHER.goForward();
    }
  }

  private class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    final JBPopup myPopup;
    final Map<ToolWindow, String> ids = new HashMap<ToolWindow, String>();
    final JList toolwindows;
    final JList files;
    final JPanel separator;
    final ToolWindowManager twManager;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel descriptions;
    final Project project;
    final int CTRL_KEY;
    final int ALT_KEY;

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
      Collections.sort(windows, new ToolWindowComparator("Project", "Changes", "Structure"));
      for (ToolWindow window : windows) {
        twModel.addElement(window);
      }
      toolwindows = new JBList(twModel);
      toolwindows.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      toolwindows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      toolwindows.setCellRenderer(new ToolWindowsRenderer(ids) {
        @Override
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);
          if (list == mouseMoveSrc && index == mouseMoveListIndex && !selected) {
            renderer.setBackground(ON_MOUSE_OVER_BG_COLOR);
          }
          return renderer;

        }
      });
      toolwindows.addKeyListener(this);
      toolwindows.addMouseListener(this);
      toolwindows.addMouseMotionListener(this);
      toolwindows.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!toolwindows.getSelectionModel().isSelectionEmpty()) {
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

      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      final VirtualFile[] openFiles = editorManager.getOpenFiles();

      try {
        Arrays.sort(openFiles, new RecentFilesComparator(project));
      } catch (Exception e) {// IndexNotReadyException
      }

      final DefaultListModel filesModel = new DefaultListModel();
      for (VirtualFile openFile : openFiles) {
        filesModel.addElement(openFile);
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
          if (list == mouseMoveSrc && index == mouseMoveListIndex && !selected) {
            renderer.setBackground(ON_MOUSE_OVER_BG_COLOR);
          }
          return renderer;
        }
      });
      files.addKeyListener(this);
      files.addMouseListener(this);
      files.addMouseMotionListener(this);
      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!files.getSelectionModel().isSelectionEmpty()) {
            toolwindows.getSelectionModel().clearSelection();
          }
        }
      });

      this.add(toolwindows, BorderLayout.WEST);
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
            final VirtualFile parent = ((VirtualFile)values[0]).getParent();
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
      if ((modifiers & Event.ALT_MASK) != 0) {
        ALT_KEY = KeyEvent.VK_CONTROL;
        CTRL_KEY = KeyEvent.VK_ALT;
      } else {
        ALT_KEY = KeyEvent.VK_ALT;
        CTRL_KEY = KeyEvent.VK_CONTROL;
      }

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
            if (focusComponent != null) {
              focusComponent.removeKeyListener(performanceProblemsSolver);
              focusComponent = null;
            }
            return true;
          }
        }).createPopup();
      myPopup.showInCenterOf(ideFrame.getContentPane());
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
      if (e.getKeyCode() == CTRL_KEY || e.getKeyCode() == KeyEvent.VK_ENTER) {
        navigate();
      } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
        goLeft();
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
        goRight();
      }
    }

    public void keyPressed(KeyEvent e) {
      switch (e.getKeyCode()) {
        case KeyEvent.VK_UP:
          goBack();
          break;
        case KeyEvent.VK_DOWN:
          goForward();
          break;
        case KeyEvent.VK_ESCAPE:
          cancel();
          break;
        case KeyEvent.VK_DELETE:
        case KeyEvent.VK_BACK_SPACE: // Mac users
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
      if (value instanceof VirtualFile) {
        final VirtualFile virtualFile = (VirtualFile)value;
        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
        if (editorManager instanceof FileEditorManagerImpl) {
          final JList jList = getSelectedList();
          ((FileEditorManagerImpl)editorManager).closeFile(virtualFile, false);
          final int selectedIndex = jList.getSelectedIndex();
          if (jList.getModel().getSize() == 1) {
            goLeft();
            ((DefaultListModel)jList.getModel()).removeElementAt(selectedIndex);
            this.remove(jList);
            this.remove(separator);
          } else {
            goForward();
            ((DefaultListModel)jList.getModel()).removeElementAt(selectedIndex);
            jList.setSize(jList.getPreferredSize());
          }
          pack();
        }
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
      return getSelectedList() == toolwindows;
    }

    private void goRight() {
      if (isFilesSelected() || !isFilesVisible()) {
        cancel();
      }
      else {
        if (files.getModel().getSize() > 0) {
          files.setSelectedIndex(Math.min(toolwindows.getSelectedIndex(), files.getModel().getSize() - 1));
          toolwindows.getSelectionModel().clearSelection();
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
        if (toolwindows.getModel().getSize() > 0) {
          toolwindows.setSelectedIndex(Math.min(files.getSelectedIndex(), toolwindows.getModel().getSize() - 1));
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
          list = isFilesSelected() ? toolwindows : files;
        }
      }
      list.setSelectedIndex(index);
    }

    public void goBack() {
      JList list = getSelectedList();
      int index = list.getSelectedIndex() - 1;
      if (index < 0) {
        if (isFilesVisible()) {
          list = isFilesSelected() ? toolwindows : files;
        }
        index = list.getModel().getSize() - 1;
      }
      list.setSelectedIndex(index);
    }

    public JList getSelectedList() {
      if (toolwindows.isSelectionEmpty() && files.isSelectionEmpty()) {
        if (files.getModel().getSize() > 1) {
          files.setSelectedIndex(0);
          return files;
        }
        else {
          toolwindows.setSelectedIndex(0);
          return toolwindows;
        }
      }
      else {
        return toolwindows.isSelectionEmpty() ? files : toolwindows;
      }
    }

    private void navigate() {
      myPopup.cancel();
      final Object value = getSelectedList().getSelectedValue();
      if (value instanceof ToolWindow) {
        ((ToolWindow)value).activate(null, true, true);
      }
      else if (value instanceof VirtualFile) {
        FileEditorManager.getInstance(project).openFile((VirtualFile)value, true);
      }
    }

    private class RecentFilesComparator implements Comparator<VirtualFile> {
      private final VirtualFile[] recentFiles;

      public RecentFilesComparator(Project project) {
        recentFiles = EditorHistoryManager.getInstance(project).getFiles();
      }

      public int compare(VirtualFile vf1, VirtualFile vf2) {
        return ArrayUtil.find(recentFiles, vf2) - ArrayUtil.find(recentFiles, vf1);
      }
    }

    private class ToolWindowComparator implements Comparator<ToolWindow> {
      private List<String> exceptions;

      public ToolWindowComparator(String... exceptions) {
        this.exceptions = Arrays.asList(exceptions);
      }
      public int compare(ToolWindow o1, ToolWindow o2) {
        final String n1 = ids.get(o1);
        final String n2 = ids.get(o2);
        for (String exception : exceptions) {
          if (n1.equals(exception)) return -1;
          if (n2.equals(exception)) return 1;
        }
        return n1.compareTo(n2);
      }
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
      toolwindows.repaint();
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
      if (value instanceof VirtualFile) {
        final VirtualFile virtualFile = (VirtualFile)value;
        final String name = virtualFile.getPresentableName();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject));

        final FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        final TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));
      }
    }
  }

  private static class ToolWindowsRenderer extends ColoredListCellRenderer {
    private static final Map<String, Icon> iconCache = new HashMap<String, Icon>();
    private final Map<ToolWindow, String> ids;

    public ToolWindowsRenderer(Map<ToolWindow, String> ids) {
      this.ids = ids;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof ToolWindow) {
        final ToolWindow tw = (ToolWindow)value;
        final String name = ids.get(tw);
        setIcon(getIcon(tw));

        final TextAttributes attributes = new TextAttributes(Color.BLACK, null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));
      }
    }

    private Icon getIcon(ToolWindow toolWindow) {
      Icon icon = iconCache.get(ids.get(toolWindow));
      if (icon != null) return icon;

      icon = toolWindow.getIcon();
      if (icon == null) {
        return Icons.UI_FORM_ICON;
      }

      icon = to16x16(icon);
      iconCache.put(ids.get(toolWindow), icon);
      return icon;
    }

    private static Icon to16x16(Icon icon) {
      if (icon.getIconHeight() == 16 && icon.getIconWidth() == 16) return icon;
      final int w = Math.min (icon.getIconWidth(), 16);
      final int h = Math.min(icon.getIconHeight(), 16);

      final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
        .createCompatibleImage(16, 16, Color.TRANSLUCENT);
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();

      final BufferedImage img = new BufferedImage(16, 16, BufferedImage.TRANSLUCENT);
      final int offX = Math.max((16 - w) / 2, 0);
      final int offY = Math.max((16 - h) / 2, 0);
      for (int col = 0; col < w; col++) {
        for (int row = 0; row < h; row++) {
          img.setRGB(col + offX, row + offY, image.getRGB(col, row));
        }
      }

      return new ImageIcon(img);
    }
  }
}
