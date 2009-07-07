package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class ToolWindowSwitcher extends AnAction {
  private static volatile ToolWindowSwitcherPanel SWITCHER = null;
  private static final Border EMPTY_BORDER = IdeBorderFactory.createEmptyBorder(1,1,1,1);
  private static final Border COLORED_BORDER = new RoundedLineBorder(Color.BLUE);
  private static final Icon EMPTY_ICON = new EmptyIcon(16, 16);

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    if (SWITCHER == null) {
      SWITCHER = new ToolWindowSwitcherPanel(project, true);
    }
      if (e.getInputEvent().isShiftDown()) {
        SWITCHER.goBack();
      } else {
        SWITCHER.goForward();
      }

  }

  private static class ToolWindowSwitcherPanel extends JPanel implements KeyListener {
    final JBPopup myPopup;
    final String[] ids;
    final JLabel[] toolwindows;
    final JLabel[] files;
    final VirtualFile[] vfiles;
    final ToolWindowManager twManager;
    final JLabel title;
    final Project project;
    ActivePanel activePanel;
    int currentIndex;
    /** tool window id <--> it's icon */
    final static Map<String, Icon> iconCache = new HashMap<String, Icon>();

    ToolWindowSwitcherPanel(Project project, boolean fromFirst) {
      this.project = project;
      setFocusable(true);
      addKeyListener(this);
      setBorder(new EmptyBorder(0,0,0,0));
      setBackground(Color.WHITE);      
      final BorderLayout layout = new BorderLayout(0, 0);
      setLayout(layout);
      twManager = ToolWindowManager.getInstance(project);
      ArrayList<String> visible = new ArrayList<String>();
      for (String id : twManager.getToolWindowIds()) {
        final ToolWindow tw = twManager.getToolWindow(id);
        if (tw.isAvailable()) {
          visible.add(id);
        }
      }
      ids = visible.toArray(new String[visible.size()]);

      currentIndex = fromFirst ? 0 : ids.length - 1;
      toolwindows = new JLabel[ids.length];

      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      final VirtualFile[] openFiles = editorManager.getOpenFiles();

      try {
        Arrays.sort(openFiles, new RecentFilesComparator(project));
      } catch (Exception e) {// IndexNotReadyException
      }

      final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
      final VirtualFile selectedFile = selectedFiles.length > 0 ? selectedFiles[0] : null;

      files = new JLabel[openFiles.length];
      final JPanel tabs = openFiles.length > 0 ? createItemsPanel("Open Files:", new Insets(0,0,0,4)) : new JPanel();
      vfiles = openFiles;
      for (int i = 0; i < openFiles.length; i++) {
        if (openFiles[i] == selectedFile) {
          currentIndex = i;
        }
        files[i] = createItem(openFiles[i].getName(), IconUtil.getIcon(openFiles[i], Iconable.ICON_FLAG_READ_STATUS, project));
        tabs.add(files[i]);
      }

      final JPanel toolwindows = createItemsPanel("Tool Windows:", new Insets(0,4,0,10));

      for (int i = 0; i < ids.length; i++) {
        this.toolwindows[i] = createItem(ids[i], getIcon(ids[i]));
        toolwindows.add(this.toolwindows[i]);
      }

      JPanel titlePanel = new JPanel();
      title = new JLabel("", SwingConstants.CENTER);
      title.setFont(new Font(title.getFont().getName(), Font.BOLD, 12));
      titlePanel.setOpaque(true);
      titlePanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
      titlePanel.add(title);

      this.add(titlePanel, BorderLayout.NORTH);
      this.add(toolwindows, BorderLayout.WEST);
      if (files.length > 0) {
        this.add(tabs, BorderLayout.EAST);
      }

      activePanel = files.length > 1 ? ActivePanel.FILES : ActivePanel.TOOL_WINDOWS;

      final IdeFrameImpl ideFrame = WindowManagerEx.getInstanceEx().getFrame(project);
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this).setResizable(false)
        .setModalContext(false).setFocusable(true).setRequestFocus(true).setMovable(false).setCancelCallback(new Computable<Boolean>() {
          public Boolean compute() {
            SWITCHER = null;
            return true;
          }
        }).createPopup();
      updateUI();
      update();
      myPopup.showInCenterOf(ideFrame.getContentPane());
    }

    private static JLabel createItem(String name, final Icon icon) {
      JLabel label = new JLabel(name, icon, SwingConstants.LEFT);
      label.setBackground(new Color(204, 204, 255));
      return label;
    }

    private static JPanel createItemsPanel(String title, Insets insets) {
      JPanel panel = new JPanel();
      panel.setBackground(Color.WHITE);
      final BoxLayout mgr = new BoxLayout(panel, BoxLayout.Y_AXIS);
      panel.setLayout(mgr);
      panel.setBorder(IdeBorderFactory.createEmptyBorder(insets));
      final JLabel label = new JLabel(title);
      label.setHorizontalAlignment(JLabel.CENTER);
      label.setForeground(Color.GRAY);
      label.setBorder(IdeBorderFactory.createEmptyBorder(2,0,3,0));
      panel.add(label);
      return panel;
    }

    public void keyTyped(KeyEvent e) {}
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

    private void goRight() {
      if (files.length == 0) return;
      
      if (activePanel == ActivePanel.TOOL_WINDOWS) {
        activePanel = ActivePanel.FILES;
        if (currentIndex >= files.length) {
          currentIndex = files.length - 1;
        }
        update();
      } else {
        myPopup.cancel();
      }
    }

    private void goLeft() {
      if (activePanel == ActivePanel.FILES) {
        activePanel = ActivePanel.TOOL_WINDOWS;
        if (currentIndex >= ids.length) {
          currentIndex = ids.length - 1;
        }
        update();
      } else {
        myPopup.cancel();
      }
    }

    public void goForward() {
      final int length = activePanel == ActivePanel.FILES ? files.length : toolwindows.length;
      currentIndex = (currentIndex == length - 1) ? 0 : currentIndex + 1;
      update();
    }

    public void goBack() {
      final int length = activePanel == ActivePanel.FILES ? files.length : toolwindows.length;
      currentIndex = (currentIndex == 0) ? length - 1 : currentIndex - 1;
      update();
    }

    private void navigate() {
      myPopup.cancel();
      if (activePanel == ActivePanel.TOOL_WINDOWS) {
        twManager.getToolWindow(ids[currentIndex]).activate(null, true, true);
      } else {
        FileEditorManager.getInstance(project).openFile(vfiles[currentIndex], true);
      }
    }

    private void update() {
      for (int i = 0; i < toolwindows.length; i++) {
        final boolean selected = i == currentIndex && activePanel == ActivePanel.TOOL_WINDOWS;
        toolwindows[i].setBorder(selected ? COLORED_BORDER : EMPTY_BORDER);
        toolwindows[i].setOpaque(selected);
      }
      for (int i = 0; i < files.length; i++) {
        final boolean selected = i == currentIndex && activePanel == ActivePanel.FILES;
        files[i].setBorder(selected ? COLORED_BORDER : EMPTY_BORDER);
        files[i].setOpaque(selected);
      }
      title.setText(activePanel == ActivePanel.FILES ? files[currentIndex].getText() : ids[currentIndex]);
    }

    private Icon getIcon(String toolWindowID) {
      Icon icon = iconCache.get(toolWindowID);
      if (icon != null) return icon;

      final ToolWindow tw = twManager.getToolWindow(toolWindowID);
      icon = tw.getIcon();
      if (icon == null) {
        return EMPTY_ICON;
      }

      icon = to16x16(icon);
      iconCache.put(toolWindowID, icon);
      return icon;
    }

    private static Icon to16x16(Icon icon) {
      if (icon.getIconHeight() == 16 && icon.getIconWidth() == 16) return icon;



      final int w = icon.getIconWidth();
      final int h = icon.getIconHeight();
      

      final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(16, 16, Color.TRANSLUCENT);
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();

      final BufferedImage img = new BufferedImage(16, 16, Color.TRANSLUCENT);
      for (int col = 0; col < 16; col++) {
        for (int row = 0; row < 16; row++) {
          img.setRGB(col, row, col < w && row < h ? image.getRGB(col, row) : Color.TRANSLUCENT);
        }
      }

      return new ImageIcon(img);
    }

    enum ActivePanel {
      TOOL_WINDOWS, FILES
    }

    private static class RecentFilesComparator implements Comparator<VirtualFile> {
      private final VirtualFile[] recentFiles;

      public RecentFilesComparator(Project project) {
        recentFiles = EditorHistoryManager.getInstance(project).getFiles();
      }

      public int compare(VirtualFile vf1, VirtualFile vf2) {
        return getIndex(vf2) - getIndex(vf1);
      }

      private int getIndex(VirtualFile vf) {
        for (int i = 0; i < recentFiles.length; i++) {
          if (recentFiles[i] == vf) return i;
        }
        return recentFiles.length - 1;
      }
    }
  }
}
