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
package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author MYakovlev
 */
public class PathEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.ui.PathEditor");
  public static final Color INVALID_COLOR = new Color(210, 0, 0);

  protected JPanel myPanel;
  private JButton myRemoveButton;
  private JButton myAddButton;
  private JButton mySpecifyUrlButton;
  private JList myList;
  private DefaultListModel myModel;
  private final Set<VirtualFile> myAllFiles = new HashSet<VirtualFile>();
  private boolean myModified = false;
  private boolean myEnabled = false;
  private static final Icon ICON_INVALID = IconLoader.getIcon("/nodes/ppInvalid.png");
  private static final Icon ICON_EMPTY = IconLoader.getIcon("/nodes/emptyNode.png");
  private final FileChooserDescriptor myDescriptor;
  private VirtualFile myAddBaseDir;

  public PathEditor(final FileChooserDescriptor descriptor) {
    myDescriptor = descriptor;
    myDescriptor.putUserData(FileChooserDialog.PREFER_LAST_OVER_TO_SELECT, Boolean.TRUE);
    myModel = createListModel();
  }

  public void setAddBaseDir(VirtualFile addBaseDir) {
    myAddBaseDir = addBaseDir;
  }

  protected void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  protected boolean isShowUrlButton() {
    return false;
  }

  protected void onSpecifyUrlButtonClicked() {
  }

  protected void setModified(boolean modified) {
    myModified = modified;
  }

  public boolean isModified() {
    return myModified;
  }

  public VirtualFile[] getRoots() {
    final int count = getRowCount();
    if (count == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile[] roots = new VirtualFile[count];
    for (int i = 0; i < count; i++) {
      roots[i] = getValueAt(i);
    }
    return roots;
  }

  public void resetPath(@NotNull List<VirtualFile> paths) {
    keepSelectionState();
    clearList();
    setEnabled(true);
    for (VirtualFile file : paths) {
      addElement(file);
    }
    setModified(false);
    updateButtons();
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout());

    myList = createList(myPanel, getListModel());

    createButtons(myPanel);

    return myPanel;
  }

  protected void createButtons(@NotNull JPanel panel) {
    Insets anInsets = new Insets(2, 2, 2, 2);
    myRemoveButton = new JButton(ProjectBundle.message("button.remove"));
    myAddButton = new JButton(ProjectBundle.message("button.add"));
    mySpecifyUrlButton = new JButton(ProjectBundle.message("sdk.paths.specify.url.button"));

    mySpecifyUrlButton.setVisible(isShowUrlButton());

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final VirtualFile[] added = doAdd();
        if (added.length > 0) {
          setModified(true);
        }
        updateButtons();
        requestDefaultFocus();
        setSelectedRoots(added);
      }
    });
    UIUtil.addKeyboardShortcut(myList, myAddButton, CommonShortcuts.getInsertKeystroke());
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int[] idxs = myList.getSelectedIndices();
        doRemoveItems(idxs, myList);
      }
    });
    UIUtil.addKeyboardShortcut(myList, myRemoveButton, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    mySpecifyUrlButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onSpecifyUrlButtonClicked();
      }
    });


    panel.add(myAddButton,
              new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, anInsets, 0, 0));
    panel.add(myRemoveButton,
              new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, anInsets, 0, 0));
    panel.add(mySpecifyUrlButton,
              new GridBagConstraints(1, 4, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, anInsets, 0, 0));
    panel.add(Box.createRigidArea(new Dimension(mySpecifyUrlButton.getPreferredSize().width, 4)),
              new GridBagConstraints(1, 5, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, anInsets, 0, 0));
  }

  protected void doRemoveItems(int[] idxs, JList list) {
    List removedItems = ListUtil.removeIndices(list, idxs);
    itemsRemoved(removedItems);
  }

  protected DefaultListModel createListModel() {
    return new DefaultListModel();
  }

  protected JBList createList(JPanel panel, DefaultListModel listModel) {
    Insets anInsets = new Insets(2, 2, 2, 2);

    JBList list = new JBList(listModel);
    list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
    list.setCellRenderer(createListCellRenderer(list));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
    scrollPane.setPreferredSize(new Dimension(500, 500));
    panel
      .add(scrollPane, new GridBagConstraints(0, 0, 1, 8, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, anInsets, 0, 0));
    return list;
  }

  protected ListCellRenderer createListCellRenderer(JBList list) {
    return new MyCellRenderer();
  }

  protected void itemsRemoved(List removedItems) {
    myAllFiles.removeAll(removedItems);
    if (removedItems.size() > 0) {
      setModified(true);
    }
    updateButtons();
    requestDefaultFocus();
  }

  private VirtualFile[] doAdd() {
    VirtualFile baseDir = myAddBaseDir;
    if (baseDir == null) {
      Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
      if (project != null) {
        baseDir = project.getBaseDir();
      }
    }
    VirtualFile[] files = FileChooser.chooseFiles(myPanel, myDescriptor, baseDir);
    files = adjustAddedFileSet(myPanel, files);
    List<VirtualFile> added = new ArrayList<VirtualFile>(files.length);
    for (VirtualFile vFile : files) {
      if (addElement(vFile)) {
        added.add(vFile);
      }
    }
    return VfsUtil.toVirtualFileArray(added);
  }

  /**
   * Implement this method to adjust adding behavior, this method is called right after the files
   * or directories are selected for added. This method allows adding UI that modify file set.
   * <p/>
   * The default implementation returns a value passed the parameter files and does nothing.
   *
   * @param component a component that could be used as a parent.
   * @param files     a selected file set
   * @return adjusted file set
   */
  protected VirtualFile[] adjustAddedFileSet(final Component component, final VirtualFile[] files) {
    return files;
  }

  public void updateButtons() {
    Object[] values = getSelectedRoots();
    myRemoveButton.setEnabled((values.length > 0) && myEnabled);
    myAddButton.setEnabled(myEnabled);
    mySpecifyUrlButton.setEnabled(myEnabled && !isUrlInserted());
    mySpecifyUrlButton.setVisible(isShowUrlButton());
  }

  private boolean isUrlInserted() {
    if (getRowCount() > 0) {
      return ((VirtualFile)getListModel().lastElement()).getFileSystem() instanceof HttpFileSystem;
    }
    return false;
  }

  protected void requestDefaultFocus() {
    if (myList != null) {
      myList.requestFocus();
    }
  }

  public void addPaths(VirtualFile... paths) {
    boolean added = false;
    keepSelectionState();
    for (final VirtualFile path : paths) {
      if (addElement(path)) {
        added = true;
      }
    }
    if (added) {
      setModified(true);
      updateButtons();
    }
  }

  public void removePaths(VirtualFile... paths) {
    final Set<VirtualFile> pathsSet = new java.util.HashSet<VirtualFile>(Arrays.asList(paths));
    int size = getRowCount();
    final TIntArrayList indicesToRemove = new TIntArrayList(paths.length);
    for (int idx = 0; idx < size; idx++) {
      VirtualFile path = getValueAt(idx);
      if (pathsSet.contains(path)) {
        indicesToRemove.add(idx);
      }
    }
    final List list = ListUtil.removeIndices(myList, indicesToRemove.toNativeArray());
    itemsRemoved(list);
  }

  /**
   * Method adds element only if it is not added yet.
   */
  protected boolean addElement(VirtualFile item) {
    if (item == null) {
      return false;
    }
    if (myAllFiles.contains(item)) {
      return false;
    }
    if (isUrlInserted()) {
      getListModel().insertElementAt(item, getRowCount() - 1);
    }
    else {
      getListModel().addElement(item);
    }
    myAllFiles.add(item);
    return true;
  }

  protected DefaultListModel getListModel() {
    return myModel;
  }

  protected void setSelectedRoots(Object[] roots) {
    ArrayList<Object> rootsList = new ArrayList<Object>(roots.length);
    for (Object root : roots) {
      if (root != null) {
        rootsList.add(root);
      }
    }
    myList.getSelectionModel().clearSelection();
    int rowCount = getRowCount();
    for (int i = 0; i < rowCount; i++) {
      Object currObject = getValueAt(i);
      LOG.assertTrue(currObject != null);
      if (rootsList.contains(currObject)) {
        myList.getSelectionModel().addSelectionInterval(i, i);
      }
    }
  }

  private void keepSelectionState() {
    final Object[] selectedItems = getSelectedRoots();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (selectedItems != null) {
          setSelectedRoots(selectedItems);
        }
      }
    });
  }

  protected Object[] getSelectedRoots() {
    return myList.getSelectedValues();
  }

  protected int getRowCount() {
    return getListModel().getSize();
  }

  protected VirtualFile getValueAt(int row) {
    return (VirtualFile)getListModel().get(row);
  }

  public void clearList() {
    getListModel().clear();
    myAllFiles.clear();
    setModified(true);
  }

  private static boolean isJarFile(final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        VirtualFile tempFile = file;
        if ((file.getFileSystem() instanceof JarFileSystem) && file.getParent() == null) {
          //[myakovlev] It was bug - directories with *.jar extensions was saved as files of JarFileSystem.
          //    so we can not just return true, we should filter such directories.
          String path = file.getPath().substring(0, file.getPath().length() - JarFileSystem.JAR_SEPARATOR.length());
          tempFile = LocalFileSystem.getInstance().findFileByPath(path);
        }
        if (tempFile != null && !tempFile.isDirectory()) {
          return Boolean.valueOf(tempFile.getFileType().equals(FileTypes.ARCHIVE));
        }
        return Boolean.FALSE;
      }
    }).booleanValue();
  }

  /**
   * @return icon for displaying parameter (ProjectRoot or VirtualFile)
   *         If parameter is not ProjectRoot or VirtualFile, returns empty icon "/nodes/emptyNode.png"
   */
  private static Icon getIconForRoot(Object projectRoot) {
    if (projectRoot instanceof VirtualFile) {
      final VirtualFile file = (VirtualFile)projectRoot;
      if (!file.isValid()) {
        return ICON_INVALID;
      }
      else if (isHttpRoot(file)) {
        return PlatformIcons.WEB_ICON;
      }
      else {
        return isJarFile(file) ? PlatformIcons.JAR_ICON : PlatformIcons.FILE_ICON;
      }
    }
    return ICON_EMPTY;
  }

  private static boolean isHttpRoot(VirtualFile virtualFileOrProjectRoot) {
    if (virtualFileOrProjectRoot != null) {
      return (virtualFileOrProjectRoot.getFileSystem() instanceof HttpFileSystem);
    }
    return false;
  }

  private final class MyCellRenderer extends DefaultListCellRenderer {
    private String getPresentableString(final Object value) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          //noinspection HardCodedStringLiteral
          return (value instanceof VirtualFile) ? ((VirtualFile)value).getPresentableUrl() : "UNKNOWN OBJECT";
        }
      });
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, getPresentableString(value), index, isSelected, cellHasFocus);
      if (isSelected) {
        setForeground(UIUtil.getListSelectionForeground());
      }
      else {
        if (value instanceof VirtualFile) {
          VirtualFile file = (VirtualFile)value;
          if (!file.isValid()) {
            setForeground(INVALID_COLOR);
          }
        }
      }
      setIcon(getIconForRoot(value));
      return this;
    }
  }
}
