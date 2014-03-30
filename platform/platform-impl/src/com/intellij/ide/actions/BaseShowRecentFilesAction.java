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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.LightColors;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

public abstract class BaseShowRecentFilesAction extends AnAction implements DumbAware {
  private static final Color BORDER_COLOR = Gray._135;

  public void actionPerformed(AnActionEvent e) {
    show(CommonDataKeys.PROJECT.getData(e.getDataContext()));
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }

  protected void show(final Project project){
    final DefaultListModel model = new DefaultListModel();

    FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile[] selectedFiles = fem.getSelectedFiles();
    VirtualFile currentFile = fem.getCurrentFile();
    VirtualFile[] files = filesToShow(project);
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();

    for(int i=files.length-1; i>= 0; i--){ // reverse order of files
      VirtualFile file = files[i];
      boolean isSelected = ArrayUtil.find(selectedFiles, file) >= 0;
      if((!isSelected || !file.equals(currentFile)) && editorProviderManager.getProviders(project, file).length > 0){
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

    final JList list = new JBList(model);
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

    final MyListSelectionListener listSelectionListener = new MyListSelectionListener(pathLabel, list);
    list.getSelectionModel().addListSelectionListener(listSelectionListener);

    Runnable runnable = new Runnable() {
      public void run() {
        Object[] values = list.getSelectedValues();
        for (Object value : values) {
          VirtualFile file = (VirtualFile) value;
          FileEditorManager.getInstance(project).openFile(file, true, true);
        }
      }
    };

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }

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

    final PopupChooserBuilder builder = new PopupChooserBuilder(list)
      .setTitle(getTitle())
      .setAdText(" ")
      .setMovable(true)
      .setItemChoosenCallback(runnable)
      .setModalContext(false)
      .addAdditionalChooseKeystroke(getAdditionalSelectKeystroke())
      .setFilteringEnabled(new Function<Object, String>() {
        public String fun(Object o) {
          return o instanceof VirtualFile ? ((VirtualFile)o).getName() : "";
        }
      });
    final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(getPeerActionId());
    final PeerListener listener = new PeerListener(project, getPeerActionId());
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut && ((KeyboardShortcut)shortcut).getSecondKeyStroke() == null) {
        final KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        builder.registerKeyboardAction(keyStroke, listener);
      }
    }
    JBPopup popup = builder.createPopup();
    listener.setPopup(popup);
    listSelectionListener.setPopup(popup);
    popup.showCenteredInCurrentWindow(project);
  }

  protected abstract String getTitle();

  protected abstract VirtualFile[] filesToShow(Project project);

  protected abstract String getPeerActionId();

  private static KeyStroke getAdditionalSelectKeystroke() {
    Shortcut[] shortcuts= KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EDIT_SOURCE);
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        return ((KeyboardShortcut) shortcut).getFirstKeyStroke();
      }
    }
    return null;
  }

  private static class RecentFilesRenderer extends ColoredListCellRenderer {
    private final Project myProject;
    public RecentFilesRenderer(Project project) {
      myProject = project;
    }

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof VirtualFile) {
        VirtualFile virtualFile = (VirtualFile)value;
        String name = virtualFile.getPresentableName();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, myProject));

        FileStatus fileStatus = FileStatusManager.getInstance(myProject).getStatus(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null , null, EffectType.LINE_UNDERSCORE,
                                                       Font.PLAIN);
        append(name, SimpleTextAttributes.fromTextAttributes(attributes));

        if (!selected && FileEditorManager.getInstance(myProject).isFileOpen(virtualFile)) {
          setBackground(LightColors.SLIGHTLY_GREEN);
        }
      }
    }
  }

  private static class PeerListener implements ActionListener {
    private final Project myProject;
    private final String myId;
    private JBPopup myPopup;

    public PeerListener(Project project, final String id) {
      myProject = project;
      myId = id;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myPopup != null) {
        myPopup.cancel();
      }
      BaseShowRecentFilesAction peer = (BaseShowRecentFilesAction)ActionManager.getInstance().getAction(myId);
      peer.show(myProject);
    }

    public void setPopup(JBPopup popup) {
      myPopup = popup;
    }
  }

  private static class MyListSelectionListener implements ListSelectionListener {
    private final JLabel myPathLabel;
    private final JList myList;
    private JBPopup myPopup;

    public MyListSelectionListener(JLabel pathLabel, JList list) {
      myPathLabel = pathLabel;
      myList = list;
    }

    private String getTitle2Text(String fullText) {
      int labelWidth = myPathLabel.getWidth();
      if (fullText == null || fullText.length() == 0) return " ";
      while (myPathLabel.getFontMetrics(myPathLabel.getFont()).stringWidth(fullText) > labelWidth) {
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
      final Object[] values = myList.getSelectedValues();
      if (values != null && values.length == 1) {
        final VirtualFile parent = ((VirtualFile)values[0]).getParent();
        if (parent != null) {
          myPathLabel.setText(getTitle2Text(parent.getPresentableUrl()));
        }
        else {
          myPathLabel.setText(" ");
        }
      }
      else {
        myPathLabel.setText(" ");
      }

      if (myPopup != null) {
        myPopup.setAdText(myPathLabel.getText(), SwingUtilities.RIGHT);
      }
    }

    public void setPopup(JBPopup popup) {
      myPopup = popup;
    }
  }

}
