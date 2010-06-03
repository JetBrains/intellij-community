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

package com.intellij.ide.projectView;

import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public abstract class BaseProjectTreeBuilder extends AbstractTreeBuilder {
  protected final Project myProject;

  public BaseProjectTreeBuilder(Project project,
                                JTree tree,
                                DefaultTreeModel treeModel,
                                AbstractTreeStructure treeStructure,
                                Comparator<NodeDescriptor> comparator) {
    init(tree, treeModel, new AbstractTreeStructure.Delegate(treeStructure) {
      @Override
      public AsyncResult revalidateElement(Object element) {
        return _revalidateElement(element);
      }
    }, comparator, DEFAULT_UPDATE_INACTIVE);
    myProject = project;
  }

  protected AbstractTreeStructure getOriginalStructure() {
    return ((AbstractTreeStructure.Delegate)getTreeStructure()).getOriginalStructure();

  }

  private AsyncResult _revalidateElement(Object element) {
    final AsyncResult<Object> result = new AsyncResult<Object>();

    if (element instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)element;
      final Object value = node.getValue();
      VirtualFile vFile = null;
      if (value instanceof PsiFileSystemItem) {
        vFile = ((PsiFileSystemItem)value).getVirtualFile();
      } else if (value instanceof PsiElement) {
        PsiFile psiFile = ((PsiElement)value).getContainingFile();
        if (psiFile != null) {
          vFile = psiFile.getVirtualFile();
        }
      }
      final ActionCallback cb = new ActionCallback();

      final VirtualFile finalVFile = vFile;
      batch(new Progressive() {
        public void run(@NotNull ProgressIndicator indicator) {
          final Ref<Object> target = new Ref<Object>();
          _select(value, finalVFile, false, Conditions.<AbstractTreeNode>alwaysTrue(), cb, indicator, target);
          cb.doWhenDone(new Runnable() {
            public void run() {
              result.setDone(target.get());
            }
          }).doWhenRejected(new Runnable() {
            public void run() {
              result.setRejected();
            }
          });
        }
      });
    } else {
      result.setRejected();
    }
    return result;
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null || ((AbstractTreeNode)nodeDescriptor).isAlwaysExpand();
  }

  protected final void expandNodeChildren(final DefaultMutableTreeNode node) {
    Object element = ((NodeDescriptor)node.getUserObject()).getElement();
    VirtualFile virtualFile = getFileToRefresh(element);
    super.expandNodeChildren(node);
    if (virtualFile != null) {
      virtualFile.refresh(true, false);
    }
  }

  private static VirtualFile getFileToRefresh(Object element) {
    return element instanceof PsiDirectory
           ? ((PsiDirectory)element).getVirtualFile()
           : element instanceof PsiFile ? ((PsiFile)element).getVirtualFile() : null;
  }

  private List<AbstractTreeNode> getOrBuildChildren(AbstractTreeNode parent) {
    buildNodeForElement(parent);

    DefaultMutableTreeNode node = getNodeForElement(parent);

    if (node == null) {
      return new ArrayList<AbstractTreeNode>();
    }

    getTree().expandPath(new TreePath(node.getPath()));

    return collectChildren(node);
  }

  private List<AbstractTreeNode> collectChildren(DefaultMutableTreeNode node) {
    int childCount = node.getChildCount();
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>(childCount);
    for (int i = 0; i < childCount; i++) {
      TreeNode childAt = node.getChildAt(i);
      DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)childAt;
      if (defaultMutableTreeNode.getUserObject() instanceof AbstractTreeNode) {
        ProjectViewNode treeNode = (ProjectViewNode)defaultMutableTreeNode.getUserObject();
        result.add(treeNode);
      }
      else if (defaultMutableTreeNode.getUserObject() instanceof FavoritesTreeNodeDescriptor) {
        AbstractTreeNode treeNode = ((FavoritesTreeNodeDescriptor)defaultMutableTreeNode.getUserObject()).getElement();
        result.add(treeNode);
      }
    }
    return result;
  }

  public ActionCallback select(Object element, VirtualFile file, final boolean requestFocus) {
    return _select(element, file, requestFocus, Conditions.<AbstractTreeNode>alwaysTrue());
  }

  public ActionCallback selectInWidth(final Object element,
                                      final boolean requestFocus,
                                      final Condition<AbstractTreeNode> nonStopCondition) {
    return _select(element, null, requestFocus, nonStopCondition);
  }

  private ActionCallback _select(final Object element,
                                 final VirtualFile file,
                                 final boolean requestFocus,
                                 final Condition<AbstractTreeNode> nonStopCondition) {

    final ActionCallback result = new ActionCallback();

    cancelUpdate().doWhenDone(new Runnable() {
      public void run() {
        batch(new Progressive() {
          public void run(@NotNull ProgressIndicator indicator) {
            _select(element, file, requestFocus, nonStopCondition, result, indicator, null);
          }
        });
      }
    });



    return result;
  }

  private void _select(Object element,
                       VirtualFile file,
                       final boolean requestFocus,
                       final Condition<AbstractTreeNode> nonStopCondition,
                       final ActionCallback result,
                       ProgressIndicator indicator,
                       @Nullable final Ref<Object> virtualSelectTarget) {
    AbstractTreeNode alreadySelected = alreadySelectedNode(element);

    final Runnable onDone = new Runnable() {
      public void run() {
        if (requestFocus && virtualSelectTarget == null) {
          IdeFocusManager.getInstance(myProject).requestFocus(getTree(), true);
        }

        result.setDone();
      }
    };

    final Condition<AbstractTreeNode> condition = new Condition<AbstractTreeNode>() {
      public boolean value(AbstractTreeNode abstractTreeNode) {
        if (result.isProcessed()) return false;
        return nonStopCondition.value(abstractTreeNode);
      }
    };

    if (alreadySelected == null) {
      expandPathTo(file, (AbstractTreeNode)getTreeStructure().getRootElement(), element, condition, indicator, virtualSelectTarget)
        .doWhenDone(new AsyncResult.Handler<AbstractTreeNode>() {
          public void run(AbstractTreeNode node) {
            if (virtualSelectTarget == null) {
              select(node, onDone);
            } else if (onDone != null) {
              onDone.run();
            }
          }
        }).notifyWhenRejected(result);
    }
    else {
      if (virtualSelectTarget == null) {
        select(alreadySelected, onDone);
      } else if (onDone != null) {
        onDone.run();
      }
    }
  }

  private AbstractTreeNode alreadySelectedNode(final Object element) {
    final TreePath[] selectionPaths = getTree().getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) {
      return null;
    }
    for (TreePath selectionPath : selectionPaths) {
      Object selected = selectionPath.getLastPathComponent();
      if (elementIsEqualTo(selected, element)) {
        return ((AbstractTreeNode)((DefaultMutableTreeNode)selected).getUserObject());
      }
    }
    return null;
  }

  private static boolean elementIsEqualTo(final Object node, final Object element) {
    if (node instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof ProjectViewNode) {
        final AbstractTreeNode projectViewNode = (ProjectViewNode)userObject;
        return projectViewNode.canRepresent(element);
      }
    }
    return false;
  }

  private AsyncResult<AbstractTreeNode> expandPathTo(final VirtualFile file,
                                                     final AbstractTreeNode root,
                                                     final Object element,
                                                     final Condition<AbstractTreeNode> nonStopCondition,
                                                     final ProgressIndicator indicator,
                                                     @Nullable final Ref<Object> target) {
    final AsyncResult<AbstractTreeNode> async = new AsyncResult<AbstractTreeNode>();

    if (root.canRepresent(element)) {
      if (target == null) {
        expand(root, new Runnable() {
          public void run() {
            async.setDone(root);
          }
        });
      } else {
        target.set(root);
        async.setDone(root);
      }
      return async;
    }

    if (root instanceof ProjectViewNode && file != null && !((ProjectViewNode)root).contains(file)) {
      async.setRejected();
      return async;
    }


    if (target == null) {
      expand(root, new Runnable() {
        public void run() {
          indicator.checkCanceled();

          final DefaultMutableTreeNode rootNode = getNodeForElement(root);
          if (rootNode != null) {
            final List<AbstractTreeNode> kids = collectChildren(rootNode);
            expandChild(kids, 0, nonStopCondition, file, element, async, indicator, target);
          }
          else {
            async.setRejected();
          }
        }
      });
    } else {
      if (indicator.isCanceled()) {
        async.setRejected();
      } else {
        final DefaultMutableTreeNode rootNode = getNodeForElement(root);
        final ArrayList<AbstractTreeNode> kids = new ArrayList<AbstractTreeNode>();
        if (rootNode != null && getTree().isExpanded(new TreePath(rootNode.getPath()))) {
          kids.addAll(collectChildren(rootNode));
        } else {
          List<Object> list = Arrays.asList(getTreeStructure().getChildElements(root));
          for (Object each : list) {
            kids.add((AbstractTreeNode)each);
          }
        }

        yield(new Runnable() {
          public void run() {
            expandChild(kids, 0, nonStopCondition, file, element, async, indicator, target);
          }
        });
      }
    }

    return async;
  }

  private void expandChild(final List<AbstractTreeNode> kids, final int i, final Condition<AbstractTreeNode> nonStopCondition, final VirtualFile file,
                           final Object element,
                           final AsyncResult<AbstractTreeNode> async, final ProgressIndicator indicator, final Ref<Object> virtualSelectTarget) {

    if (i >= kids.size()) {
      async.setRejected();
      return;
    }

    final AbstractTreeNode eachKid = kids.get(i);
    final boolean[] nodeWasCollapsed = new boolean[] {true};
    final DefaultMutableTreeNode nodeForElement = getNodeForElement(eachKid);
    if (nodeForElement != null) {
      nodeWasCollapsed[0] = getTree().isCollapsed(new TreePath(nodeForElement.getPath()));
    }

    if (nonStopCondition.value(eachKid)) {
      expandPathTo(file, eachKid, element, nonStopCondition, indicator, virtualSelectTarget).doWhenDone(new AsyncResult.Handler<AbstractTreeNode>() {
        public void run(AbstractTreeNode abstractTreeNode) {
          indicator.checkCanceled();

          async.setDone(abstractTreeNode);
        }
      }).doWhenRejected(new Runnable() {
        public void run() {
          indicator.checkCanceled();

          if (nodeWasCollapsed[0] && virtualSelectTarget == null) {
            collapseChildren(eachKid, null);
          }
          expandChild(kids, i + 1, nonStopCondition, file, element, async, indicator, virtualSelectTarget);
        }
      });
    } else {
      async.setRejected();
    }
  }

  protected boolean validateNode(final Object child) {
    if (child instanceof ProjectViewNode) {
      final ProjectViewNode projectViewNode = (ProjectViewNode)child;
      return projectViewNode.validate();
    }
    return true;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
