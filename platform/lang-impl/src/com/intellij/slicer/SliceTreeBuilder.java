// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class SliceTreeBuilder {
  private final SliceTreeStructure sliceTreeStructure;
  public final boolean splitByLeafExpressions;
  public final boolean dataFlowToThis;
  public volatile boolean analysisInProgress;

  public static final Comparator<NodeDescriptor<?>> SLICE_NODE_COMPARATOR = (o1, o2) -> {
    if (!(o1 instanceof SliceNode) || !(o2 instanceof SliceNode)) {
      return AlphaComparator.INSTANCE.compare(o1, o2);
    }
    SliceNode node1 = (SliceNode)o1;
    SliceNode node2 = (SliceNode)o2;
    SliceUsage usage1 = node1.getValue();
    SliceUsage usage2 = node2.getValue();

    PsiElement element1 = usage1 == null ? null : usage1.getElement();
    PsiElement element2 = usage2 == null ? null : usage2.getElement();

    PsiFile file1 = element1 == null ? null : element1.getContainingFile();
    PsiFile file2 = element2 == null ? null : element2.getContainingFile();

    if (file1 == null) return file2 == null ? 0 : 1;
    if (file2 == null) return -1;

    if (file1 == file2) {
      return element1.getTextOffset() - element2.getTextOffset();
    }

    return Comparing.compare(file1.getName(), file2.getName());
  };

  SliceTreeBuilder(@NotNull SliceTreeStructure sliceTreeStructure,
                   boolean dataFlowToThis,
                   boolean splitByLeafExpressions) {
    this.sliceTreeStructure = sliceTreeStructure;
    this.dataFlowToThis = dataFlowToThis;
    this.splitByLeafExpressions = splitByLeafExpressions;
  }

  public SliceTreeStructure getTreeStructure() {
    return sliceTreeStructure;
  }

  public SliceRootNode getRootSliceNode() {
    return sliceTreeStructure.getRootElement();
  }

  void switchToGroupedByLeavesNodes() {
    SliceLanguageSupportProvider provider = getRootSliceNode().getProvider();
    if(provider == null){
      return;
    }
    analysisInProgress = true;
    provider.startAnalyzeLeafValues(sliceTreeStructure, () -> analysisInProgress = false);
  }

  public void switchToLeafNulls() {
    SliceLanguageSupportProvider provider = getRootSliceNode().getProvider();
    if(provider == null){
      return;
    }
    analysisInProgress = true;
    provider.startAnalyzeNullness(sliceTreeStructure, () -> analysisInProgress = false);
  }
}
