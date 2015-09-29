/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DuplicateNodeRenderer;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceNode extends AbstractTreeNode<SliceUsage> implements DuplicateNodeRenderer.DuplicatableNode<SliceNode>, MyColoredTreeCellRenderer {
  protected List<SliceNode> myCachedChildren;
  protected boolean dupNodeCalculated;
  protected SliceNode duplicate;
  protected final DuplicateMap targetEqualUsages;
  protected boolean changed;
  private int index; // my index in parent's mycachedchildren

  protected SliceNode(@NotNull Project project, SliceUsage sliceUsage, @NotNull DuplicateMap targetEqualUsages) {
    super(project, sliceUsage);
    this.targetEqualUsages = targetEqualUsages;
  }

  @NotNull
  SliceNode copy() {
    SliceUsage newUsage = getValue().copy();
    SliceNode newNode = new SliceNode(getProject(), newUsage, targetEqualUsages);
    newNode.dupNodeCalculated = dupNodeCalculated;
    newNode.duplicate = duplicate;
    return newNode;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
    ProgressIndicator indicator = current == null ? new ProgressIndicatorBase() : current;
    if (current == null) {
      indicator.start();
    }
    final Collection[] nodes = new Collection[1];
    ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        nodes[0] = getChildrenUnderProgress(ProgressManager.getInstance().getProgressIndicator());
      }
    }, indicator);
    if (current == null) {
      indicator.stop();
    }
    return nodes[0];
  }

  SliceNode getNext(List parentChildren) {
    return index == parentChildren.size() - 1 ? null : (SliceNode)parentChildren.get(index + 1);
  }

  SliceNode getPrev(List parentChildren) {
    return index == 0 ? null : (SliceNode)parentChildren.get(index - 1);
  }

  @NotNull
  protected List<? extends AbstractTreeNode> getChildrenUnderProgress(@NotNull final ProgressIndicator progress) {
    if (isUpToDate()) return myCachedChildren == null ? Collections.<AbstractTreeNode>emptyList() : myCachedChildren;
    final List<SliceNode> children = new ArrayList<SliceNode>();
    final SliceManager manager = SliceManager.getInstance(getProject());
    manager.runInterruptibly(progress, new Runnable() {
      @Override
      public void run() {
        changed = true;
        //SwingUtilities.invokeLater(new Runnable() {
        //  public void run() {
        //    if (getTreeBuilder().isDisposed()) return;
        //    DefaultMutableTreeNode node = getTreeBuilder().getNodeForElement(getValue());
        //    //myTreeBuilder.getUi().queueBackgroundUpdate(node, (NodeDescriptor)node.getUserObject(), new TreeUpdatePass(node));
        //    if (node == null) node = getTreeBuilder().getRootNode();
        //    getTreeBuilder().addSubtreeToUpdate(node);
        //  }
        //});
      }
    }, new Runnable() {
      @Override
      public void run() {
        Processor<SliceUsage> processor = new Processor<SliceUsage>() {
          @Override
          public boolean process(SliceUsage sliceUsage) {
            progress.checkCanceled();
            SliceNode node = new SliceNode(myProject, sliceUsage, targetEqualUsages);
            synchronized (children) {
              node.index = children.size();
              children.add(node);
            }
            return true;
          }
        };

        getValue().processChildren(processor);
      }
    }
    );

    synchronized (children) {
      myCachedChildren = children;
    }
    return children;
  }

  private boolean isUpToDate() {
    if (myCachedChildren != null || !isValid()/* || getTreeBuilder().splitByLeafExpressions*/) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  protected PresentationData createPresentation() {
    return new PresentationData(){
      @NotNull
      @Override
      public Object[] getEqualityObjects() {
        return ArrayUtil.append(super.getEqualityObjects(), changed);
      }
    };
  }

  @Override
  protected void update(PresentationData presentation) {
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
    }
  }

  public void calculateDupNode() {
    if (!dupNodeCalculated) {
      if (!(getValue() instanceof SliceTooComplexDFAUsage)) {
        duplicate = targetEqualUsages.putNodeCheckDupe(this);
      }
      dupNodeCalculated = true;
    }
  }

  @Override
  public SliceNode getDuplicate() {
    return duplicate;
  }

  @Override
  public void navigate(boolean requestFocus) {
    SliceUsage sliceUsage = getValue();
    sliceUsage.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }

  public boolean isValid() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return getValue().isValid();
      }
    });
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Override
  public void customizeCellRenderer(@NotNull SliceUsageCellRendererBase renderer, @NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    renderer.setIcon(getPresentation().getIcon(expanded));
    if (isValid()) {
      SliceUsage sliceUsage = getValue();
      renderer.customizeCellRendererFor(sliceUsage);
      renderer.setToolTipText(sliceUsage.getPresentation().getTooltipText());
    }
    else {
      renderer.append(UsageViewBundle.message("node.invalid") + " ", SliceUsageCellRendererBase.ourInvalidAttributes);
    }
  }

  public void setChanged() {
    changed = true;
  }

  @Nullable
  public SliceLanguageSupportProvider getProvider(){
    AbstractTreeNode<SliceUsage> element = getElement();
    if(element == null){
      return null;
    }
    SliceUsage usage = element.getValue();
    if(usage == null){
      return null;
    }
    PsiElement psiElement = usage.getElement();
    if(psiElement == null){
      return null;
    }
    return LanguageSlicing.getProvider(psiElement);
  }

  @Override
  public String toString() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Override
            public String compute() {
              return getValue()==null?"<null>":getValue().toString();
            }
          });
  }

}
