package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class ToolWindowSwitcher extends AnAction {
  private static volatile ToolWindowSwitcherPanel SWITCHER = null;
  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);
  private static final Color SEPARATOR_COLOR = BORDER_COLOR.brighter();

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    if (SWITCHER == null) {
      SWITCHER = new ToolWindowSwitcherPanel(project);
    }
    if (e.getInputEvent().isShiftDown()) {
      SWITCHER.goBack();
    }
    else {
      SWITCHER.goForward();
    }
  }

  private static class ToolWindowSwitcherPanel extends JPanel implements KeyListener {
    final JBPopup myPopup;
    final Map<ToolWindow, String> ids = new HashMap<ToolWindow, String>();
    final JList toolwindows;
    final JList files;
    final ToolWindowManager twManager;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel descriptions;
    final Project project;

    ToolWindowSwitcherPanel(Project project) {
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
          twModel.addElement(tw);
        }
      }
      toolwindows = new JList(twModel);
      toolwindows.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      toolwindows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      toolwindows.setCellRenderer(new ToolWindowsRenderer(ids));
      toolwindows.addKeyListener(this);
      toolwindows.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!toolwindows.getSelectionModel().isSelectionEmpty()) {
            files.getSelectionModel().clearSelection();
          }
        }
      });

      final JPanel separator = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          g.setColor(SEPARATOR_COLOR);
          g.drawLine(0, 0, 0, getHeight());
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
      files = new JList(filesModel);
      files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      files.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 20));
      files.setCellRenderer(new VirtualFilesRenderer(project));
      files.addKeyListener(this);
      files.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          if (!files.getSelectionModel().isSelectionEmpty()) {
            toolwindows.getSelectionModel().clearSelection();
          }
        }
      });

      this.add(toolwindows, BorderLayout.WEST);
      if (filesModel.size() > 0) {
        this.add(files, BorderLayout.EAST);
      }
      this.add(descriptions, BorderLayout.SOUTH);
      this.add(separator, BorderLayout.CENTER);

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
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
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

      final IdeFrameImpl ideFrame = WindowManagerEx.getInstanceEx().getFrame(project);
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
          .setResizable(false)
          .setModalContext(false)
          .setFocusable(true)
          .setRequestFocus(true)
          .setMovable(true)
          .setTitle("Switcher")
          .setCancelCallback(new Computable<Boolean>() {
          public Boolean compute() {
            SWITCHER = null;
            return true;
          }
        }).createPopup();
      myPopup.showInCenterOf(ideFrame.getContentPane());
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
      switch (e.getKeyCode()) {
        case KeyEvent.VK_LEFT:
          goLeft();
          break;
        case KeyEvent.VK_RIGHT:
          goRight();
          break;
        case KeyEvent.VK_UP:
          goBack();
          break;
        case KeyEvent.VK_DOWN:
          goForward();
          break;
        case KeyEvent.VK_ESCAPE:
          cancel();
          break;
        case KeyEvent.VK_ALT:
          if (isFilesSelected()) {
            goLeft();
          } else {
            goRight();
          }
          break;
      }
    }

    public void keyReleased(KeyEvent e) {
      switch (e.getKeyCode()) {
        case KeyEvent.VK_CONTROL:
        case KeyEvent.VK_ENTER:
          navigate();
          break;
      }
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
      if (isFilesSelected()) {
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

    private static class RecentFilesComparator implements Comparator<VirtualFile> {
      private final VirtualFile[] recentFiles;

      public RecentFilesComparator(Project project) {
        recentFiles = EditorHistoryManager.getInstance(project).getFiles();
      }

      public int compare(VirtualFile vf1, VirtualFile vf2) {
        return ArrayUtil.find(recentFiles, vf2) - ArrayUtil.find(recentFiles, vf1);
      }
    }

  }

  private static class VirtualFilesRenderer extends ColoredListCellRenderer {
    private final Project myProject;

    public VirtualFilesRenderer(Project project) {
      myProject = project;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof VirtualFile) {
        VirtualFile virtualFile = (VirtualFile)value;
        String name = virtualFile.getName();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject));

        FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
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
        String name = ids.get(tw);
        setIcon(getIcon(tw));

        TextAttributes attributes = new TextAttributes(Color.BLACK, null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
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
      final int w = icon.getIconWidth();
      final int h = icon.getIconHeight();

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
