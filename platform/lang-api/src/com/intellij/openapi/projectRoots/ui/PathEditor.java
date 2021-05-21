// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author MYakovlev
 */
public class PathEditor {
  private static final Logger LOG = Logger.getInstance(PathEditor.class);

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

  public void resetPath(@NotNull List<? extends VirtualFile> paths) {
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
    myList.setCellRenderer(createListCellRenderer(myList));
    TreeUIHelper.getInstance().installListSpeedSearch(myList, VirtualFile::getPresentableUrl);

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myList)
      .disableUpDownActions()
      .setAddActionUpdater(e -> myEnabled)
      .setRemoveActionUpdater(e -> isRemoveActionEnabled(getSelectedRoots()))
      .setAddAction(button -> {
        final VirtualFile[] added = doAddItems();
        if (added.length > 0) {
          setModified(true);
        }
        requestDefaultFocus();
        setSelectedRoots(added);
      })
      .setRemoveAction(button -> {
        int[] indices = myList.getSelectedIndices();
        doRemoveItems(indices, myList);
      });

    addToolbarButtons(toolbarDecorator);

    myPanel = toolbarDecorator.createPanel();
    myPanel.setBorder(null);

    return myPanel;
  }

  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) { }

  protected boolean isRemoveActionEnabled(VirtualFile[] files) {
    return files.length > 0 && myEnabled;
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

  protected void doRemoveItems(int[] indices, JList<VirtualFile> list) {
    itemsRemoved(ListUtil.removeIndices(list, indices));
  }

  protected DefaultListModel<VirtualFile> createListModel() {
    return new DefaultListModel<>();
  }

  protected ListCellRenderer<VirtualFile> createListCellRenderer(JBList<VirtualFile> list) {
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
    final Set<VirtualFile> pathsSet = ContainerUtil.set(paths);
    int size = getRowCount();
    final IntList indicesToRemove = new IntArrayList(paths.length);
    for (int idx = 0; idx < size; idx++) {
      VirtualFile path = getValueAt(idx);
      if (pathsSet.contains(path)) {
        indicesToRemove.add(idx);
      }
    }
    itemsRemoved(ListUtil.removeIndices(myList, indicesToRemove.toIntArray()));
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

  protected void setSelectedRoots(VirtualFile[] roots) {
    Set<VirtualFile> set = ContainerUtil.newHashSet(roots);
    myList.getSelectionModel().clearSelection();
    for (int i = 0, rowCount = getRowCount(); i < rowCount; i++) {
      Object currObject = getValueAt(i);
      LOG.assertTrue(currObject != null);
      if (set.contains(currObject)) {
        myList.getSelectionModel().addSelectionInterval(i, i);
      }
    }
  }

  private void keepSelectionState() {
    VirtualFile[] selectedItems = getSelectedRoots();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> setSelectedRoots(selectedItems));
  }

  protected VirtualFile[] getSelectedRoots() {
    return VfsUtilCore.toVirtualFileArray(myList.getSelectedValuesList());
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

  protected static class PathCellRenderer extends ColoredListCellRenderer<VirtualFile> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends VirtualFile> list, VirtualFile file, int index, boolean selected, boolean focused) {
      LOG.assertTrue(file != null);
      String text = file.getPresentableUrl();
      append(text, file.isValid() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
      setIcon(getItemIcon(file));
    }

    protected Icon getItemIcon(@NotNull VirtualFile file) {
      if (!file.isValid()) return AllIcons.Nodes.PpInvalid;
      if (file.getFileSystem() instanceof HttpFileSystem) return PlatformIcons.WEB_ICON;
      if (file.getFileSystem() instanceof ArchiveFileSystem) return PlatformIcons.JAR_ICON;
      return PlatformIcons.FILE_ICON;
    }
  }
}