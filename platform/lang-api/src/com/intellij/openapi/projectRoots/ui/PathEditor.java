// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author MYakovlev
 */
public class PathEditor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.ui.PathEditor");

  public static final Color INVALID_COLOR = new JBColor(new Color(210, 0, 0), JBColor.RED);

  protected JPanel myPanel;
  private JBList<VirtualFile> myList;
  private final DefaultListModel<VirtualFile> myModel;
  private final Set<VirtualFile> myAllFiles = new HashSet<>();
  private boolean myModified = false;
  protected boolean myEnabled = false;
  private final FileChooserDescriptor myDescriptor;
  private VirtualFile myAddBaseDir;

  public PathEditor(final FileChooserDescriptor descriptor) {
    myDescriptor = descriptor;
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
    myList = new JBList<>(getListModel());
    //noinspection unchecked
    myList.setCellRenderer(createListCellRenderer(myList));
    TreeUIHelper.getInstance().installListSpeedSearch(myList, VirtualFile::getPresentableUrl);

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myList)
      .disableUpDownActions()
      .setAddActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myEnabled;
        }
      })
      .setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return isRemoveActionEnabled(PathEditor.this.getSelectedRoots());
        }
      })
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final VirtualFile[] added = doAddItems();
          if (added.length > 0) {
            setModified(true);
          }
          requestDefaultFocus();
          setSelectedRoots(added);
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int[] indices = myList.getSelectedIndices();
          doRemoveItems(indices, myList);
        }
      });

    addToolbarButtons(toolbarDecorator);

    myPanel = toolbarDecorator.createPanel();
    myPanel.setBorder(null);

    return myPanel;
  }

  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
  }

  protected boolean isRemoveActionEnabled(Object[] values) {
    return values.length > 0 && myEnabled;
  }

  protected VirtualFile[] doAddItems() {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
    VirtualFile[] files = FileChooser.chooseFiles(myDescriptor, myPanel, project, myAddBaseDir);
    if (files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    files = adjustAddedFileSet(myPanel, files);
    List<VirtualFile> added = new ArrayList<>(files.length);
    for (VirtualFile vFile : files) {
      if (addElement(vFile)) {
        added.add(vFile);
      }
    }
    return VfsUtilCore.toVirtualFileArray(added);
  }

  protected void doRemoveItems(int[] indices, JList list) {
    List removedItems = ListUtil.removeIndices(list, indices);
    itemsRemoved(removedItems);
  }

  protected DefaultListModel<VirtualFile> createListModel() {
    return new DefaultListModel<>();
  }

  protected ListCellRenderer createListCellRenderer(JBList list) {
    return new PathCellRenderer();
  }

  protected void itemsRemoved(List<VirtualFile> removedItems) {
    myAllFiles.removeAll(removedItems);
    if (removedItems.size() > 0) {
      setModified(true);
    }
    requestDefaultFocus();
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
      return getListModel().lastElement().getFileSystem() instanceof HttpFileSystem;
    }
    return false;
  }

  protected void requestDefaultFocus() {
    if (myList != null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
        () -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
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
    final Set<VirtualFile> pathsSet = new HashSet<>(Arrays.asList(paths));
    int size = getRowCount();
    final TIntArrayList indicesToRemove = new TIntArrayList(paths.length);
    for (int idx = 0; idx < size; idx++) {
      VirtualFile path = getValueAt(idx);
      if (pathsSet.contains(path)) {
        indicesToRemove.add(idx);
      }
    }
    final List<VirtualFile> list = ListUtil.removeIndices(myList, indicesToRemove.toNativeArray());
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

  protected DefaultListModel<VirtualFile> getListModel() {
    return myModel;
  }

  protected void setSelectedRoots(Object[] roots) {
    ArrayList<Object> rootsList = new ArrayList<>(roots.length);
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
    if (selectedItems != null) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> setSelectedRoots(selectedItems));
    }
  }

  @SuppressWarnings("deprecation")
  protected Object[] getSelectedRoots() {
    return myList.getSelectedValues();
  }

  protected int getRowCount() {
    return getListModel().getSize();
  }

  protected VirtualFile getValueAt(int row) {
    return getListModel().get(row);
  }

  public void clearList() {
    getListModel().clear();
    myAllFiles.clear();
    setModified(true);
  }

  private static boolean isJarFile(final VirtualFile file) {
    return ReadAction.compute(() -> {
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
    }).booleanValue();
  }

  private static Icon getIconForRoot(Object projectRoot) {
    if (projectRoot instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)projectRoot;
      if (!file.isValid()) {
        return AllIcons.Nodes.PpInvalid;
      }
      else if (file.getFileSystem() instanceof HttpFileSystem) {
        return PlatformIcons.WEB_ICON;
      }
      else if (isJarFile(file)) {
        return PlatformIcons.JAR_ICON;
      }
      return PlatformIcons.FILE_ICON;
    }

    return AllIcons.Nodes.EmptyNode;
  }

  protected static class PathCellRenderer extends DefaultListCellRenderer {
    protected String getItemText(Object value) {
      return value instanceof VirtualFile ? ((VirtualFile)value).getPresentableUrl() : "UNKNOWN OBJECT";
    }

    protected Icon getItemIcon(Object value) {
      return getIconForRoot(value);
    }

    @Override
    public final Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, getItemText(value), index, isSelected, cellHasFocus);

      if (isSelected) {
        setForeground(UIUtil.getListSelectionForeground());
      }
      else if (value instanceof VirtualFile && !((VirtualFile)value).isValid()) {
        setForeground(INVALID_COLOR);
      }

      setIcon(getItemIcon(value));

      return this;
    }
  }
}
