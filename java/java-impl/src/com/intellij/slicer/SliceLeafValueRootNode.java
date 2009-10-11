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
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.impl.NullUsage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author cdr
 */
public class SliceLeafValueRootNode extends AbstractTreeNode<Usage> implements MyColoredTreeCellRenderer {
  final List<SliceNode> myCachedChildren;

  protected SliceLeafValueRootNode(@NotNull Project project, PsiElement leafExpression, SliceNode root) {
    super(project, leafExpression == PsiUtilBase.NULL_PSI_ELEMENT ? NullUsage.INSTANCE : new UsageInfo2UsageAdapter(new UsageInfo(leafExpression)));

    Set<PsiElement> withLeaves = ContainerUtil.singleton(leafExpression, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
    SliceNode node = root.copy(withLeaves);
    myCachedChildren = Collections.singletonList(node);
    restructureChildrenByLeaf(node, root, leafExpression, withLeaves, null);
  }

  private static void restructureChildrenByLeaf(SliceNode node, SliceNode oldRoot, @Nullable PsiElement leafExpression, Set<PsiElement> withLeaves,
                                                SliceNode parent) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    node.myCachedChildren = children;
    assert oldRoot.getLeafExpressions().contains(leafExpression);
    boolean iAmHereToStay = false;
    for (AbstractTreeNode cachedChild : oldRoot.myCachedChildren) {
      SliceNode cachedSliceNode = (SliceNode)cachedChild;
      if (cachedSliceNode.getDuplicate() != null) {
        // put entire (potentially unbounded) subtree here
        //children.add(cachedSliceNode.copy(withLeaves));
      }
      else if (cachedSliceNode.getLeafExpressions().contains(leafExpression)) {
        SliceNode newNode = cachedSliceNode.copy(withLeaves);
        children.add(newNode);
        PsiElement element = newNode.getValue().getElement();
        if (element != null && element.getManager().areElementsEquivalent(element, leafExpression)) {
          iAmHereToStay = true;
        }
        if (!cachedSliceNode.myCachedChildren.isEmpty()) {
          restructureChildrenByLeaf(newNode, cachedSliceNode, leafExpression, withLeaves, node);
        }
      }
    }

    if (!iAmHereToStay && children.isEmpty() && parent != null) {
      parent.myCachedChildren.remove(node);
    }
  }

  @NotNull
  public Collection<SliceNode> getChildren() {
    return myCachedChildren;
  }

  @Override
  protected void update(PresentationData presentation) {
  }

  @Override
  public String toString() {
    Usage myLeafExpression = getValue();
    String text;
    if (myLeafExpression instanceof UsageInfo2UsageAdapter) {
      PsiElement element = ((UsageInfo2UsageAdapter)myLeafExpression).getUsageInfo().getElement();
      text = element == null ? "" : element.getText();
    }
    else {
      text = "Other";
    }
    return "Value: "+ text;
  }

  public boolean isValid() {
    return getValue().isValid();
  }

  public void customizeCellRenderer(SliceUsageCellRenderer renderer,
                                    JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    Usage usage = getValue();
    renderer.append("Value: ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (usage instanceof UsageInfo2UsageAdapter) {
      PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
      if (element == null) {
        renderer.append(UsageViewBundle.message("node.invalid") + " ", SliceUsageCellRenderer.ourInvalidAttributes);
      }
      else {
        renderer.append(element.getText(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
    else {
      renderer.append("Other", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }
}