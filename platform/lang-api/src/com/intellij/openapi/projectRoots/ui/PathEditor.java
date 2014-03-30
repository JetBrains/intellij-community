/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author MYakovlev
 */
public class PathEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.ui.PathEditor");

  public static final Color INVALID_COLOR = new JBColor(new Color(210, 0, 0), JBColor.RED);

  protected JPanel myPanel;
  private JBList myList;
  private final DefaultListModel myModel;
  private final Set<VirtualFile> myAllFiles = new HashSet<VirtualFile>();
  private boolean myModified = false;
  protected boolean myEnabled = false;
  private final FileChooserDescriptor myDescriptor;
  private VirtualFile myAddBaseDir;

  public PathEditor(final FileChooserDescriptor descriptor) {
    myDescriptor = descriptor;
    myDescriptor.putUserData(FileChooserDialog.PREFER_LAST_OVER_TO_SELECT, Boolean.TRUE);
    myModel = createListModel();
  }

  public void setAddBaseDir(@Nullable VirtualFile addBaseDir) {
    myAddBaseDir = addBaseDir;
  }

  protected void setEnabled(boolean enabled) {
    myEnabled = enabled;
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
  }

  public JComponent createComponent() {
    myList = new JBList(getListModel());
    myList.setCellRenderer(createListCellRenderer(myList));

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final VirtualFile[] added = doAdd();
          if (added.length > 0) {
            setModified(true);
          }
          requestDefaultFocus();
          setSelectedRoots(added);
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int[] idxs = myList.getSelectedIndices();
          doRemoveItems(idxs, myList);
        }
      }).setAddActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myEnabled;
        }
      }).setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          Object[] values = getSelectedRoots();
          return values.length > 0 && myEnabled;
        }
      });

    addToolbarButtons(toolbarDecorator);

    myPanel = toolbarDecorator.createPanel();
    myPanel.setBorder(null);

    return myPanel;
  }

  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
  }

  protected void doRemoveItems(int[] idxs, JList list) {
    List removedItems = ListUtil.removeIndices(list, idxs);
    itemsRemoved(removedItems);
  }

  protected DefaultListModel createListModel() {
    return new DefaultListModel();
  }

  protected ListCellRenderer createListCellRenderer(JBList list) {
    return new MyCellRenderer();
  }

  protected void itemsRemoved(List removedItems) {
    myAllFiles.removeAll(removedItems);
    if (removedItems.size() > 0) {
      setModified(true);
    }
    requestDefaultFocus();
  }

  private VirtualFile[] doAdd() {
    VirtualFile baseDir = myAddBaseDir;
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
    if (baseDir == null && project != null) {
      baseDir = project.getBaseDir();
    }
    VirtualFile[] files = FileChooser.chooseFiles(myDescriptor, myPanel, project, baseDir);
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

  protected boolean isUrlInserted() {
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
      @Override
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
      @Override
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
        return AllIcons.Nodes.PpInvalid;
      }
      else if (isHttpRoot(file)) {
        return PlatformIcons.WEB_ICON;
      }
      else {
        return isJarFile(file) ? PlatformIcons.JAR_ICON : PlatformIcons.FILE_ICON;
      }
    }
    return AllIcons.Nodes.EmptyNode;
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
        @Override
        public String compute() {
          //noinspection HardCodedStringLiteral
          return (value instanceof VirtualFile) ? ((VirtualFile)value).getPresentableUrl() : "UNKNOWN OBJECT";
        }
      });
    }

    @Override
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
