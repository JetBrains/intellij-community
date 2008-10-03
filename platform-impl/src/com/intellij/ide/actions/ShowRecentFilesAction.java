
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

public class ShowRecentFilesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files");
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    show(project);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }

  private static void show(final Project project){
    final DefaultListModel model = new DefaultListModel();

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    
    VirtualFile[] files = EditorHistoryManager.getInstance(project).getFiles();
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();

    for(int i=files.length-1; i>= 0; i--){ // reverse order of files
      VirtualFile file = files[i];
      if(ArrayUtil.find(selectedFiles, file) == -1 && editorProviderManager.getProviders(project, file).length > 0){
        // 1. do not put currently selected file
        // 2. do not include file with no corresponding editor providers
        model.addElement(file);
      }
    }

    final JLabel pathLabel = new JLabel(" ");
    pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    if (true /*SystemInfo.isMac*/) {
      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont((float)10));
    }

    final JList list = new JList(model);
    list.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            int index = list.getSelectedIndex();
            if (index == -1 || index >= list.getModel().getSize()){
              return;
            }
            Object[] values = list.getSelectedValues();
            for (Object value : values) {
              VirtualFile file = (VirtualFile) value;
              model.removeElement(file);
              if (model.getSize() > 0) {
                if (model.getSize() == index) {
                  list.setSelectedIndex(model.getSize() - 1);
                } else if (model.getSize() > index) {
                  list.setSelectedIndex(index);
                }
              } else {
                list.clearSelection();
              }
              EditorHistoryManager.getInstance(project).removeFile(file);
            }
          }
        }
      }
    );

    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
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
        final Object[] values = list.getSelectedValues();
        if (values != null && values.length == 1) {
          pathLabel.setText(getTitle2Text(((VirtualFile)values[0]).getPresentableUrl()));
        }
        else {
          pathLabel.setText(" ");
        }
      }
    });

    Runnable runnable = new Runnable() {
      public void run() {
        Object[] values = list.getSelectedValues();
        for (Object value : values) {
          VirtualFile file = (VirtualFile) value;
          FileEditorManager.getInstance(project).openFile(file, true);
        }
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }
    new MyListSpeedSearch(list);
    list.setCellRenderer(new RecentFilesRenderer(project));

    /*
    TODO:
    if (model.getSize() > 0) {
      Dimension listPreferredSize = list.getPreferredSize();
      list.setVisibleRowCount(0);
      Dimension viewPreferredSize = new Dimension(listPreferredSize.width, Math.min(listPreferredSize.height, r.height - 20));
      ((JViewport)list.getParent()).setPreferredSize(viewPreferredSize);
    }
    */

    JPanel footerPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(BORDER_COLOR);
        g.drawLine(0, 0, getWidth(), 0);
      }
    };

    footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    footerPanel.add(pathLabel);

    new PopupChooserBuilder(list).
        setTitle(IdeBundle.message("title.popup.recent.files")).
        setMovable(true).
        setSouthComponent(footerPanel).
        setItemChoosenCallback(runnable).
        addAdditionalChooseKeystroke(getAdditionalSelectKeystroke()).
        createPopup().showCenteredInCurrentWindow(project);
  }

  private static final Color BORDER_COLOR = new Color(0x87, 0x87, 0x87);

  private static KeyStroke getAdditionalSelectKeystroke() {
    Shortcut[] shortcuts=KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EDIT_SOURCE);
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        return ((KeyboardShortcut) shortcut).getFirstKeyStroke();
      }
    }
    return null;
  }


  private static class RecentFilesRenderer extends ColoredListCellRenderer {
    private final Project myProject;
    private static final Icon IN_TAB = IconLoader.findIcon("/ide/tab.png");
    private static final EmptyIcon NOT_IN_TAB = new EmptyIcon(IN_TAB.getIconWidth(), IN_TAB.getIconHeight());

    public RecentFilesRenderer(Project project) {
      myProject = project;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof VirtualFile) {
        VirtualFile virtualFile = (VirtualFile)value;
        String name = virtualFile.getName();

        Icon baseIcon = IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject);

        final RowIcon row = new RowIcon(2);
        row.setIcon(baseIcon, 1);
        row.setIcon(FileEditorManager.getInstance(myProject).isFileOpen(virtualFile) ? IN_TAB : NOT_IN_TAB, 0);

        setIcon(row);

        FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE,
                                                       Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));
      }
    }
  }

  private static class MyListSpeedSearch extends ListSpeedSearch {
    public MyListSpeedSearch(JList list) {
      super(list);
    }

    protected String getElementText(Object element) {
      return element instanceof VirtualFile ? ((VirtualFile)element).getName() : null;
    }
  }
}
