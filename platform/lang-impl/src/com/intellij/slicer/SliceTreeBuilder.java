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
package com.intellij.slicer;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

/**
 * @author cdr
 */
public class SliceTreeBuilder extends AbstractTreeBuilder {
  final boolean splitByLeafExpressions;
  public final boolean dataFlowToThis;
  volatile boolean analysisInProgress;

  static final Comparator<NodeDescriptor> SLICE_NODE_COMPARATOR = (o1, o2) -> {
    if (!(o1 instanceof SliceNode) || !(o2 instanceof SliceNode)) {
      return AlphaComparator.INSTANCE.compare(o1, o2);
    }
    SliceNode node1 = (SliceNode)o1;
    SliceNode node2 = (SliceNode)o2;
    SliceUsage usage1 = node1.getValue();
    SliceUsage usage2 = node2.getValue();

    PsiElement element1 = usage1.getElement();
    PsiElement element2 = usage2.getElement();

    PsiFile file1 = element1 == null ? null : element1.getContainingFile();
    PsiFile file2 = element2 == null ? null : element2.getContainingFile();

    if (file1 == null) return file2 == null ? 0 : 1;
    if (file2 == null) return -1;

    if (file1 == file2) {
      return element1.getTextOffset() - element2.getTextOffset();
    }

    return Comparing.compare(file1.getName(), file2.getName());
  };

  SliceTreeBuilder(@NotNull JTree tree,
                   @NotNull Project project,
                   boolean dataFlowToThis,
                   @NotNull SliceNode rootNode,
                   boolean splitByLeafExpressions) {
    super(tree, (DefaultTreeModel)tree.getModel(), new SliceTreeStructure(project, rootNode), SLICE_NODE_COMPARATOR, false);
    this.dataFlowToThis = dataFlowToThis;
    this.splitByLeafExpressions = splitByLeafExpressions;
    initRootNode();
  }

  SliceNode getRootSliceNode() {
    return (SliceNode)getTreeStructure().getRootElement();
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  void switchToGroupedByLeavesNodes() {
    SliceLanguageSupportProvider provider = getRootSliceNode().getProvider();
    if(provider == null){
      return;
    }
    analysisInProgress = true;
    provider.startAnalyzeLeafValues(getTreeStructure(), () -> analysisInProgress = false);
  }


  void switchToLeafNulls() {
    SliceLanguageSupportProvider provider = getRootSliceNode().getProvider();
    if(provider == null){
      return;
    }
    analysisInProgress = true;
    provider.startAnalyzeLeafValues(getTreeStructure(), () -> analysisInProgress = false);

    analysisInProgress = true;
    provider.startAnalyzeNullness(getTreeStructure(), () -> analysisInProgress = false);
  }
}
