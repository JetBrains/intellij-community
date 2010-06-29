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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleExcludedStateAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleSourcesStateAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.roots.ToolbarPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 9, 2003
 * Time: 1:19:47 PM
 */
public class ContentEntryTreeEditor {
  private final Project myProject;
  private final boolean myCanMarkSources;
  private final boolean myCanMarkTestSources;
  protected Tree myTree;
  private FileSystemTreeImpl myFileSystemTree;
  private final JPanel myTreePanel;
  private final DefaultMutableTreeNode EMPTY_TREE_ROOT = new DefaultMutableTreeNode(ProjectBundle.message("module.paths.empty.node"));
  protected DefaultActionGroup myEditingActionsGroup;
  private ContentEntryEditor myContentEntryEditor;
  private final MyContentEntryEditorListener myContentEntryEditorListener = new MyContentEntryEditorListener();
  private final FileChooserDescriptor myDescriptor;

  public ContentEntryTreeEditor(Project project, boolean canMarkSources, boolean canMarkTestSources) {
    myProject = project;
    myCanMarkSources = canMarkSources;
    myCanMarkTestSources = canMarkTestSources;
    myTree = new Tree();
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    myEditingActionsGroup = new DefaultActionGroup();

    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);

    myTreePanel = new MyPanel(new BorderLayout());
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    myTreePanel.add(new ToolbarPanel(scrollPane, myEditingActionsGroup), BorderLayout.CENTER);

    myTreePanel.setVisible(false);
    myDescriptor = new FileChooserDescriptor(false, true, false, false, false, true);
    myDescriptor.setShowFileSystemRoots(false);
  }

  protected void createEditingActions() {
    if (myCanMarkSources) {
      ToggleSourcesStateAction markSourcesAction = new ToggleSourcesStateAction(myTree, this, false);
      markSourcesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_MASK)), myTree);
      myEditingActionsGroup.add(markSourcesAction);
    }

    if (myCanMarkTestSources) {
      setupTestsAction();
    }

    setupExcludedAction();
  }

  protected TreeCellRenderer getContentEntryCellRenderer() {
    return new ContentEntryTreeCellRenderer(this);
  }

  /**
   * @param contentEntryEditor : null means to clear the editor
   */
  public void setContentEntryEditor(ContentEntryEditor contentEntryEditor) {
    if (myContentEntryEditor != null && myContentEntryEditor.equals(contentEntryEditor)) {
      return;
    }
    if (myFileSystemTree != null) {
      Disposer.dispose(myFileSystemTree);
      myFileSystemTree = null;
    }
    if (myContentEntryEditor != null) {
      myContentEntryEditor.removeContentEntryEditorListener(myContentEntryEditorListener);
      myContentEntryEditor = null;
    }
    if (contentEntryEditor == null) {
      ((DefaultTreeModel)myTree.getModel()).setRoot(EMPTY_TREE_ROOT);
      myTreePanel.setVisible(false);
      if (myFileSystemTree != null) {
        Disposer.dispose(myFileSystemTree);
      }
      return;
    }
    myTreePanel.setVisible(true);
    myContentEntryEditor = contentEntryEditor;
    myContentEntryEditor.addContentEntryEditorListener(myContentEntryEditorListener);
    final VirtualFile file = contentEntryEditor.getContentEntry().getFile();
    myDescriptor.setRoot(file);
    if (file != null) {
      myDescriptor.setTitle(file.getPresentableUrl());
    }
    else {
      final String url = contentEntryEditor.getContentEntry().getUrl();
      myDescriptor.setTitle(VirtualFileManager.extractPath(url).replace('/', File.separatorChar));
    }


    final Runnable init = new Runnable() {
      public void run() {
        myFileSystemTree.updateTree();
        if (file != null) {
          select(file);
        }
      }
    };


    myFileSystemTree = new FileSystemTreeImpl(myProject, myDescriptor, myTree, getContentEntryCellRenderer(), init) {
      protected AbstractTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure,
                                                      Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor,
                                                      final Runnable onInitialized) {
        return new MyFileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized);
      }
    };
    myFileSystemTree.showHiddens(true);


    Disposer.register(myProject, myFileSystemTree);


    final NewFolderAction newFolderAction = new MyNewFolderAction();
    DefaultActionGroup mousePopupGroup = new DefaultActionGroup();
    mousePopupGroup.add(myEditingActionsGroup);
    mousePopupGroup.addSeparator();
    mousePopupGroup.add(newFolderAction);
    myFileSystemTree.registerMouseListener(mousePopupGroup);

  }

  public ContentEntryEditor getContentEntryEditor() {
    return myContentEntryEditor;
  }

  public JComponent createComponent() {
    createEditingActions();
    return myTreePanel;
  }

  public void select(VirtualFile file) {
    if (myFileSystemTree != null) {
      myFileSystemTree.select(file, null);
    }
  }

  public void requestFocus() {
    myTree.requestFocus();
  }

  public void update() {
    if (myFileSystemTree != null) {
      myFileSystemTree.updateTree();
      final DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
      final int visibleRowCount = myTree.getVisibleRowCount();
      for (int row = 0; row < visibleRowCount; row++) {
        final TreePath pathForRow = myTree.getPathForRow(row);
        if (pathForRow != null) {
          final TreeNode node = (TreeNode)pathForRow.getLastPathComponent();
          if (node != null) {
            model.nodeChanged(node);
          }
        }
      }
    }
  }

  private class MyContentEntryEditorListener extends ContentEntryEditorListenerAdapter {
    public void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder) {
      update();
    }

    public void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource) {
      update();
    }

    public void folderExcluded(ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    public void folderIncluded(ContentEntryEditor editor, VirtualFile file) {
      update();
    }

    public void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder) {
      update();
    }
  }

  private static class MyNewFolderAction extends NewFolderAction implements CustomComponentAction {
    private MyNewFolderAction() {
      super(ActionsBundle.message("action.FileChooser.NewFolder.text"),
            ActionsBundle.message("action.FileChooser.NewFolder.description"),
            IconLoader.getIcon("/actions/newFolder.png"));
    }

    public JComponent createCustomComponent(Presentation presentation) {
      return IconWithTextAction.createCustomComponentImpl(this, presentation);
    }
  }

  private static class MyFileTreeBuilder extends FileTreeBuilder {
    public MyFileTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor, @Nullable Runnable onInitialized) {
      super(tree, treeModel, treeStructure, comparator, descriptor, onInitialized);
    }

    protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
      return false; // need this in order to not show plus for empty directories
    }
  }

  private class MyPanel extends JPanel implements DataProvider {
    private MyPanel(final LayoutManager layout) {
      super(layout);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (FileSystemTree.DATA_KEY.is(dataId)) {
        return myFileSystemTree;
      }
      return null;
    }
  }

  protected void setupTestsAction() {
    ToggleSourcesStateAction markTestsAction = new ToggleSourcesStateAction(myTree, this, true);
    markTestsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK)), myTree);
    myEditingActionsGroup.add(markTestsAction);
  }

  protected void setupExcludedAction() {
    ToggleExcludedStateAction toggleExcludedAction = new ToggleExcludedStateAction(myTree, this);
    myEditingActionsGroup.add(toggleExcludedAction);
    toggleExcludedAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK)), myTree);
  }

}
