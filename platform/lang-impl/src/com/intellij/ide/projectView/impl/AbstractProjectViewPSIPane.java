/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.stripe.ErrorStripe;
import com.intellij.ui.stripe.ErrorStripePainter;
import com.intellij.ui.stripe.TreeUpdater;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.StringTokenizer;

public abstract class AbstractProjectViewPSIPane extends AbstractProjectViewPane {
  private JScrollPane myComponent;

  protected AbstractProjectViewPSIPane(Project project) {
    super(project);
  }

  @Override
  public JComponent createComponent() {
    if (myComponent != null) return myComponent;

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = createTree(treeModel);
    enableDnD();
    myComponent = ScrollPaneFactory.createScrollPane(myTree);
    if (Registry.is("error.stripe.enabled")) {
      ErrorStripePainter painter = new ErrorStripePainter(true);
      Disposer.register(this, new TreeUpdater<ErrorStripePainter>(painter, myComponent, myTree) {
        @Override
        protected void update(ErrorStripePainter painter, int index, Object object) {
          if (object instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)object;
            object = node.getUserObject();
          }
          if (object instanceof PsiDirectoryNode && !myTree.isCollapsed(index)) {
            object = null;
          }
          super.update(painter, index, object);
        }

        @Override
        protected ErrorStripe getErrorStripe(Object object) {
          if (object instanceof PresentableNodeDescriptor) {
            PresentableNodeDescriptor node = (PresentableNodeDescriptor)object;
            PresentationData presentation = node.getPresentation();
            TextAttributesKey key = presentation.getTextAttributesKey();
            if (key != null) {
              TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
              if (attributes != null && EffectType.WAVE_UNDERSCORE == attributes.getEffectType()) {
                return ErrorStripe.create(attributes.getEffectColor(), 1);
              }
            }
          }
          return null;
        }
      });
    }
    myTreeStructure = createStructure();
    setTreeBuilder(createBuilder(treeModel));

    installComparator();
    initTree();

    Disposer.register(getTreeBuilder(), new UiNotifyConnector(myTree, new Activatable() {
      private boolean showing;

      @Override
      public void showNotify() {
        if (!showing) {
          showing = true;
          restoreExpandedPaths();
        }
      }

      @Override
      public void hideNotify() {
        if (showing) {
          showing = false;
          saveExpandedPaths();
        }
      }
    }));
    return myComponent;
  }

  @Override
  public final void dispose() {
    myComponent = null;
    super.dispose();
  }

  private void initTree() {
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandPath(new TreePath(myTree.getModel().getRoot()));
    myTree.setSelectionPath(new TreePath(myTree.getModel().getRoot()));

    EditSourceOnDoubleClickHandler.install(myTree);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        fireTreeChangeListener();
      }
    });
    myTree.getModel().addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        fireTreeChangeListener();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        fireTreeChangeListener();
      }
    });

    new MySpeedSearch(myTree);

    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {

          final DefaultMutableTreeNode selectedNode = ((ProjectViewTree)myTree).getSelectedNode();
          if (selectedNode != null && !selectedNode.isLeaf()) {
            return;
          }

          DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
          OpenSourceUtil.openSourcesFrom(dataContext, ScreenReader.isActive());
        }
        else if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          if (e.isConsumed()) return;
          PsiCopyPasteManager copyPasteManager = PsiCopyPasteManager.getInstance();
          boolean[] isCopied = new boolean[1];
          if (copyPasteManager.getElements(isCopied) != null && !isCopied[0]) {
            copyPasteManager.clear();
            e.consume();
          }
        }
      }
    });
    CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP);
  }

  @NotNull
  @Override
  public final ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    final ArrayList<Object> pathsToExpand = new ArrayList<>();
    final ArrayList<Object> selectionPaths = new ArrayList<>();
    Runnable afterUpdate;
    final ActionCallback cb = new ActionCallback();
    if (restoreExpandedPaths) {
      TreeBuilderUtil.storePaths(getTreeBuilder(), (DefaultMutableTreeNode)myTree.getModel().getRoot(), pathsToExpand, selectionPaths, true);
      afterUpdate = () -> {
        if (myTree != null && getTreeBuilder() != null && !getTreeBuilder().isDisposed()) {
          myTree.setSelectionPaths(new TreePath[0]);
          TreeBuilderUtil.restorePaths(getTreeBuilder(), pathsToExpand, selectionPaths, true);
        }
        cb.setDone();
      };
    }
    else {
      afterUpdate = cb.createSetDoneRunnable();
    }
    if (getTreeBuilder() != null) {
      getTreeBuilder().addSubtreeToUpdate(getTreeBuilder().getRootNode(), afterUpdate);
    }
    //myTreeBuilder.updateFromRoot();
    return cb;
  }

  @Override
  public void select(Object element, VirtualFile file, boolean requestFocus) {
    selectCB(element, file, requestFocus);
  }

  @NotNull
  public ActionCallback selectCB(Object element, VirtualFile file, boolean requestFocus) {
    if (file != null) {
      return ((BaseProjectTreeBuilder)getTreeBuilder()).select(element, file, requestFocus);
    }
    return ActionCallback.DONE;
  }

  @NotNull
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
      @Override
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
  }

  protected abstract ProjectAbstractTreeStructureBase createStructure();

  protected abstract ProjectViewTree createTree(DefaultTreeModel treeModel);

  protected abstract AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder);


  protected static final class MySpeedSearch extends TreeSpeedSearch {
    MySpeedSearch(JTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      Object userObject = ((DefaultMutableTreeNode)((TreePath)element).getLastPathComponent()).getUserObject();
      if (userObject instanceof PsiDirectoryNode) {
        String str = getElementText(element);
        if (str == null) return false;
        str = str.toLowerCase();
        if (pattern.indexOf('.') >= 0) {
          return compare(str, pattern);
        }
        StringTokenizer tokenizer = new StringTokenizer(str, ".");
        while (tokenizer.hasMoreTokens()) {
          String token = tokenizer.nextToken();
          if (compare(token, pattern)) {
            return true;
          }
        }
        return false;
      }
      else {
        return super.isMatchingElement(element, pattern);
      }
    }
  }
}
