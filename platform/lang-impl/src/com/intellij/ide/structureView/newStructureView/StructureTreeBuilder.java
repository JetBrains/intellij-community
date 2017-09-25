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

package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.GroupWrapper;
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class StructureTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;
  private final StructureViewModel myStructureModel;

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
  
  public StructureTreeBuilder(Project project,
                              JTree tree,
                              DefaultTreeModel treeModel,
                              AbstractTreeStructure treeStructure,
                              StructureViewModel structureModel) {
    super(
      tree,
      treeModel,
      treeStructure, null, false
    );

    myProject = project;
    myStructureModel = structureModel;

    final ModelListener myModelListener = new ModelListener() {
      @Override
      public void onModelChanged() {
        addRootToUpdate();
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new MyPsiTreeChangeListener(), this);

    CopyPasteUtil.addDefaultListener(this, this::addSubtreeToUpdateByElement);
    initRootNode();
    myStructureModel.addModelListener(myModelListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myStructureModel.removeModelListener(myModelListener);
      }
    });

    setCanYieldUpdate(!ApplicationManager.getApplication().isUnitTestMode());
  }

  @Override
  protected final boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  @Override
  protected final boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    StructureViewModel model = myStructureModel;
    if (model instanceof TreeModelWrapper) {
      model = ((TreeModelWrapper) model).getModel();
    }
    if (model instanceof StructureViewModel.ExpandInfoProvider) {
      StructureViewModel.ExpandInfoProvider provider = (StructureViewModel.ExpandInfoProvider)model;
      Object element = nodeDescriptor.getElement();
      if (element instanceof StructureViewComponent.StructureViewTreeElementWrapper) {
        StructureViewComponent.StructureViewTreeElementWrapper wrapper = (StructureViewComponent.StructureViewTreeElementWrapper)element;
        if (wrapper.getValue() instanceof StructureViewTreeElement) {
          final StructureViewTreeElement value = (StructureViewTreeElement)wrapper.getValue();
          if (value != null) {
            return provider.isAutoExpand(value);
          }
        }
      } else if (element instanceof GroupWrapper) {
        final Group group = ((GroupWrapper)element).getValue();
        for (TreeElement treeElement : group.getChildren()) {
          if (treeElement instanceof StructureViewTreeElement && !provider.isAutoExpand((StructureViewTreeElement)treeElement)) {
            return false;
          }
        }
      }
    }
    // expand root node & its immediate children
    final NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
    return super.isAutoExpandNode(parent == null ? nodeDescriptor : parent);
  }

  @Override
  protected final boolean isSmartExpand() {
    StructureViewModel model = myStructureModel;
    if (model instanceof TreeModelWrapper) {
      model = ((TreeModelWrapper) model).getModel();
    }
    if (model instanceof StructureViewModel.ExpandInfoProvider) {
      return ((StructureViewModel.ExpandInfoProvider)model).isSmartExpand();
    }
    return false;
  }

  @Override
  @NotNull
  protected final ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    private final PsiModificationTracker myModificationTracker;
    private long myOutOfCodeBlockModificationCount;

    private MyPsiTreeChangeListener() {
      myModificationTracker = PsiManager.getInstance(myProject).getModificationTracker();
      myOutOfCodeBlockModificationCount = myModificationTracker.getOutOfCodeBlockModificationCount();
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization

      childrenChanged();
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      PsiElement oldChild = event.getOldChild();
      PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }

    private void childrenChanged() {
      long newModificationCount = myModificationTracker.getOutOfCodeBlockModificationCount();
      if (newModificationCount == myOutOfCodeBlockModificationCount) return;
      myOutOfCodeBlockModificationCount = newModificationCount;
      setupUpdateAlarm();
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      childrenChanged();
    }
  }

  private void setupUpdateAlarm() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> {
      if (!isDisposed() && !myProject.isDisposed()) {
        addRootToUpdate();
      }
    }, 300, ModalityState.stateForComponent(getTree()));
  }

  final void addRootToUpdate() {
    final AbstractTreeStructure structure = getTreeStructure();
    structure.asyncCommit().doWhenDone(() -> {
      ((SmartTreeStructure)structure).rebuildTree();
      if (!isDisposed()) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }
    });
  }

  @Override
  @NotNull
  protected final AbstractTreeNode createSearchingTreeNodeWrapper() {
    return new StructureViewComponent.StructureViewTreeElementWrapper(null,null, null);
  }
}
