/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
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
import com.intellij.openapi.wm.FocusRequestor;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class BaseProjectTreeBuilder extends AbstractTreeBuilder {
  protected final Project myProject;

  public BaseProjectTreeBuilder(@NotNull Project project,
                                @NotNull JTree tree,
                                @NotNull DefaultTreeModel treeModel,
                                @NotNull AbstractTreeStructure treeStructure,
                                @Nullable Comparator<NodeDescriptor> comparator) {
    init(tree, treeModel, treeStructure, comparator, DEFAULT_UPDATE_INACTIVE);
    myProject = project;
  }

  @NotNull
  @Override
  public AsyncResult<Object> revalidateElement(Object element) {
    final AsyncResult<Object> result = new AsyncResult<>();

    if (element instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)element;
      final Object value = node.getValue();
      final ActionCallback callback = new ActionCallback();
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(ObjectUtils.tryCast(value, PsiElement.class));
      final FocusRequestor focusRequestor = IdeFocusManager.getInstance(myProject).getFurtherRequestor();
      batch(new Progressive() {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final Ref<Object> target = new Ref<>();
          _select(value, virtualFile, false, Conditions.<AbstractTreeNode>alwaysTrue(), callback, indicator, target, focusRequestor, false);
          callback.doWhenDone(() -> result.setDone(target.get())).doWhenRejected(() -> result.setRejected());
        }
      });
    }
    else {
      result.setRejected();
    }
    return result;
  }


  @Override
  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null || ((AbstractTreeNode)nodeDescriptor).isAlwaysExpand();
  }

  @Override
  protected final void expandNodeChildren(@NotNull final DefaultMutableTreeNode node) {
    final NodeDescriptor userObject = (NodeDescriptor)node.getUserObject();
    if (userObject == null) return;
    Object element = userObject.getElement();
    VirtualFile virtualFile = getFileToRefresh(element);
    super.expandNodeChildren(node);
    if (virtualFile != null) {
      virtualFile.refresh(true, false);
    }
  }

  private static VirtualFile getFileToRefresh(Object element) {
    Object object = element;
    if (element instanceof AbstractTreeNode) {
      object = ((AbstractTreeNode)element).getValue();
    }
    
    return object instanceof PsiDirectory
           ? ((PsiDirectory)object).getVirtualFile()
           : object instanceof PsiFile ? ((PsiFile)object).getVirtualFile() : null;
  }

  @NotNull
  private static List<AbstractTreeNode> collectChildren(@NotNull DefaultMutableTreeNode node) {
    int childCount = node.getChildCount();
    List<AbstractTreeNode> result = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      TreeNode childAt = node.getChildAt(i);
      DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)childAt;
      if (defaultMutableTreeNode.getUserObject() instanceof AbstractTreeNode) {
        AbstractTreeNode treeNode = (AbstractTreeNode)defaultMutableTreeNode.getUserObject();
        result.add(treeNode);
      }
      else if (defaultMutableTreeNode.getUserObject() instanceof FavoritesTreeNodeDescriptor) {
        AbstractTreeNode treeNode = ((FavoritesTreeNodeDescriptor)defaultMutableTreeNode.getUserObject()).getElement();
        result.add(treeNode);
      }
    }
    return result;
  }

  @NotNull
  public ActionCallback select(Object element, VirtualFile file, final boolean requestFocus) {
    return _select(element, file, requestFocus, Conditions.<AbstractTreeNode>alwaysTrue());
  }

  public ActionCallback selectInWidth(final Object element,
                                      final boolean requestFocus,
                                      final Condition<AbstractTreeNode> nonStopCondition) {
    return _select(element, null, requestFocus, nonStopCondition);
  }

  @NotNull
  private ActionCallback _select(final Object element,
                                 final VirtualFile file,
                                 final boolean requestFocus,
                                 final Condition<AbstractTreeNode> nonStopCondition) {

    final ActionCallback result = new ActionCallback();

    final FocusRequestor requestor = IdeFocusManager.getInstance(myProject).getFurtherRequestor();

    UiActivityMonitor.getInstance().addActivity(myProject, new UiActivity.AsyncBgOperation("projectViewSelect"), getUpdater().getModalityState());
    cancelUpdate().doWhenDone(() -> batch(new Progressive() {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        _select(element, file, requestFocus, nonStopCondition, result, indicator, null, requestor, false);
        UiActivityMonitor.getInstance().removeActivity(myProject, new UiActivity.AsyncBgOperation("projectViewSelect"));
      }
    }));



    return result;
  }

  private void _select(final Object element,
                       final VirtualFile file,
                       final boolean requestFocus,
                       final Condition<AbstractTreeNode> nonStopCondition,
                       final ActionCallback result,
                       @NotNull final ProgressIndicator indicator,
                       @Nullable final Ref<Object> virtualSelectTarget,
                       final FocusRequestor focusRequestor,
                       final boolean isSecondAttempt) {
    final AbstractTreeNode alreadySelected = alreadySelectedNode(element);

    final Runnable onDone = () -> {
      if (requestFocus && virtualSelectTarget == null && getUi().isReady()) {
        focusRequestor.requestFocus(getTree(), true);
      }

      result.setDone();
    };

    final Condition<AbstractTreeNode> condition = abstractTreeNode -> !result.isProcessed() && nonStopCondition.value(abstractTreeNode);

    if (alreadySelected == null) {
      expandPathTo(file, (AbstractTreeNode)getTreeStructure().getRootElement(), element, condition, indicator, virtualSelectTarget)
        .doWhenDone(new Consumer<AbstractTreeNode>() {
          @Override
          public void consume(AbstractTreeNode node) {
            if (virtualSelectTarget == null) {
              select(node, onDone);
            }
            else {
              onDone.run();
            }
          }
        }).doWhenRejected(() -> {
          if (isSecondAttempt) {
            result.setRejected();
          } else {
            _select(file, file, requestFocus, nonStopCondition, result, indicator, virtualSelectTarget, focusRequestor, true);
          }
        });
    }
    else if (virtualSelectTarget == null && getTree().getSelectionPaths().length == 1) {
      select(alreadySelected, onDone);
    }
    else {
      onDone.run();
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

  @NotNull
  private AsyncResult<AbstractTreeNode> expandPathTo(final VirtualFile file,
                                                     @NotNull final AbstractTreeNode root,
                                                     final Object element,
                                                     @NotNull final Condition<AbstractTreeNode> nonStopCondition,
                                                     @NotNull final ProgressIndicator indicator,
                                                     @Nullable final Ref<Object> target) {
    final AsyncResult<AbstractTreeNode> async = new AsyncResult<>();

    if (root.canRepresent(element)) {
      if (target == null) {
        expand(root, () -> async.setDone(root));
      }
      else {
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
      expand(root, () -> {
        indicator.checkCanceled();

        final DefaultMutableTreeNode rootNode = getNodeForElement(root);
        if (rootNode != null) {
          final List<AbstractTreeNode> kids = collectChildren(rootNode);
          expandChild(kids, 0, nonStopCondition, file, element, async, indicator, target);
        }
        else {
          async.setRejected();
        }
      });
    }
    else {
      if (indicator.isCanceled()) {
        async.setRejected();
      }
      else {
        final DefaultMutableTreeNode rootNode = getNodeForElement(root);
        final ArrayList<AbstractTreeNode> kids = new ArrayList<>();
        if (rootNode != null && getTree().isExpanded(new TreePath(rootNode.getPath()))) {
          kids.addAll(collectChildren(rootNode));
        }
        else {
          List<Object> list = Arrays.asList(getTreeStructure().getChildElements(root));
          for (Object each : list) {
            kids.add((AbstractTreeNode)each);
          }
        }

        yield(() -> {
          if (isDisposed()) return;
          expandChild(kids, 0, nonStopCondition, file, element, async, indicator, target);
        });
      }
    }

    return async;
  }

  private void expandChild(@NotNull final List<AbstractTreeNode> kids,
                           int i,
                           @NotNull final Condition<AbstractTreeNode> nonStopCondition,
                           final VirtualFile file,
                           final Object element,
                           @NotNull final AsyncResult<AbstractTreeNode> async,
                           @NotNull final ProgressIndicator indicator,
                           final Ref<Object> virtualSelectTarget) {
    while (i < kids.size()) {
      final AbstractTreeNode eachKid = kids.get(i);
      final boolean[] nodeWasCollapsed = {true};
      final DefaultMutableTreeNode nodeForElement = getNodeForElement(eachKid);
      if (nodeForElement != null) {
        nodeWasCollapsed[0] = getTree().isCollapsed(new TreePath(nodeForElement.getPath()));
      }

      if (nonStopCondition.value(eachKid)) {
        final AsyncResult<AbstractTreeNode> result = expandPathTo(file, eachKid, element, nonStopCondition, indicator, virtualSelectTarget);
        result.doWhenDone(new Consumer<AbstractTreeNode>() {
          @Override
          public void consume(AbstractTreeNode abstractTreeNode) {
            indicator.checkCanceled();
            async.setDone(abstractTreeNode);
          }
        });

        if (!result.isProcessed()) {
          final int next = i + 1;
          result.doWhenRejected(() -> {
            indicator.checkCanceled();

            if (nodeWasCollapsed[0] && virtualSelectTarget == null) {
              collapseChildren(eachKid, null);
            }
            expandChild(kids, next, nonStopCondition, file, element, async, indicator, virtualSelectTarget);
          });
          return;
        } else {
          if (result.isRejected()) {
            indicator.checkCanceled();
            if (nodeWasCollapsed[0] && virtualSelectTarget == null) {
              collapseChildren(eachKid, null);
            }
            i++;
          } else {
            return;
          }
        }
      } else {
        //filter tells us to stop here (for instance, in case of module nodes)
        break;
      }
    }
    async.setRejected();
  }

  @Override
  protected boolean validateNode(final Object child) {
    if (child == null) {
      return false;
    }
    if (child instanceof ProjectViewNode) {
      final ProjectViewNode projectViewNode = (ProjectViewNode)child;
      return projectViewNode.validate();
    }
    return true;
  }

  @Override
  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
