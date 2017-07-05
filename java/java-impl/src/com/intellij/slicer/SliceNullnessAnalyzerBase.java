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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.WalkingState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class SliceNullnessAnalyzerBase {
  @NotNull
  private final SliceLeafEquality myLeafEquality;

  @NotNull
  private final SliceLanguageSupportProvider myProvider;

  public SliceNullnessAnalyzerBase(@NotNull SliceLeafEquality leafEquality,
                                   @NotNull SliceLanguageSupportProvider provider) {
    myLeafEquality = leafEquality;
    myProvider = provider;
  }

  private void groupByNullness(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = createNewTree(result, oldRoot, map);

    SliceUsage rootUsage = oldRoot.myCachedChildren.get(0).getValue();
    SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, SliceManager.getElementDescription(null, rootUsage.getElement(), " Grouped by Nullness") );
  }

  @NotNull
  public SliceRootNode createNewTree(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = oldRoot.copy();
    assert oldRoot.myCachedChildren.size() == 1;
    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    root.setChanged();
    root.targetEqualUsages.clear();
    root.myCachedChildren = new ArrayList<>();

    createValueRootNode(result, oldRoot, map, root, oldRootStart, "Null Values", NullAnalysisResult.NULLS);
    createValueRootNode(result, oldRoot, map, root, oldRootStart, "NotNull Values", NullAnalysisResult.NOT_NULLS);
    createValueRootNode(result, oldRoot, map, root, oldRootStart, "Other Values", NullAnalysisResult.UNKNOWNS);

    return root;
  }

  private void createValueRootNode(NullAnalysisResult result,
                                   SliceRootNode oldRoot,
                                   final Map<SliceNode, NullAnalysisResult> map,
                                   SliceRootNode root,
                                   SliceNode oldRootStart,
                                   String nodeName,
                                   final int group) {
    Collection<PsiElement> groupedByValue = result.groupedByValue[group];
    if (groupedByValue.isEmpty()) {
      return;
    }
    SliceLeafValueClassNode valueRoot = new SliceLeafValueClassNode(root.getProject(), root, nodeName);
    root.myCachedChildren.add(valueRoot);

    Set<PsiElement> uniqueValues = new THashSet<>(groupedByValue, myLeafEquality);
    for (final PsiElement expression : uniqueValues) {
      SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, oldNode -> {
        if (oldNode.getDuplicate() != null) {
          return null;
        }

        for (PsiElement nullSuspect : group(oldNode, map, group)) {
          if (PsiEquivalenceUtil.areElementsEquivalent(nullSuspect, expression)) {
            return oldNode.copy();
          }
        }
        return null;
      }, (node, children) -> {
        if (!children.isEmpty()) return true;
        PsiElement element = node.getValue().getElement();
        if (element == null) return false;
        return PsiEquivalenceUtil.areElementsEquivalent(element, expression); // leaf can be there only if it's filtering expression
      });
      valueRoot.myCachedChildren.add(
        new SliceLeafValueRootNode(root.getProject(),
                                   valueRoot,
                                   myProvider.createRootUsage(expression, oldRoot.getValue().params),
                                   Collections.singletonList(newRoot))
      );
    }
  }

  public void startAnalyzeNullness(@NotNull AbstractTreeStructure treeStructure, @NotNull Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<NullAnalysisResult> leafExpressions = Ref.create(null);
    final Map<SliceNode, NullAnalysisResult> map = createMap();

    ProgressManager.getInstance().run(new Task.Backgroundable(root.getProject(), "Expanding all nodes... (may very well take the whole day)", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        NullAnalysisResult l = calcNullableLeaves(root, treeStructure, map);
        leafExpressions.set(l);
      }

      @Override
      public void onCancel() {
        finish.run();
      }

      @Override
      public void onSuccess() {
        try {
          NullAnalysisResult leaves = leafExpressions.get();
          if (leaves == null) return;  //cancelled

          groupByNullness(leaves, root, map);
        }
        finally {
          finish.run();
        }
      }
    });
  }

  public static Map<SliceNode, NullAnalysisResult> createMap() {
    return FactoryMap.createMap(k->new NullAnalysisResult(), ContainerUtil::newIdentityTroveMap);
  }

  private static NullAnalysisResult node(@NotNull SliceNode node, @NotNull Map<SliceNode, NullAnalysisResult> nulls) {
    return nulls.get(node);
  }
  private static Collection<PsiElement> group(@NotNull SliceNode node, @NotNull Map<SliceNode, NullAnalysisResult> nulls, int group) {
    return nulls.get(node).groupedByValue[group];
  }

  @NotNull
  public NullAnalysisResult calcNullableLeaves(@NotNull final SliceNode root,
                                               @NotNull AbstractTreeStructure treeStructure,
                                               @NotNull final Map<SliceNode, NullAnalysisResult> map) {
    final SliceLeafAnalyzer.SliceNodeGuide guide = new SliceLeafAnalyzer.SliceNodeGuide(treeStructure);
    WalkingState<SliceNode> walkingState = new WalkingState<SliceNode>(guide) {
      @Override
      public void visit(@NotNull final SliceNode element) {
        element.calculateDupNode();
        node(element, map).clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          node(element, map).add(node(duplicate, map));
        }
        else {
          final PsiElement value = ReadAction.compute(() -> element.getValue().getElement());
          Nullness nullness = ReadAction.compute(() -> checkNullness(value));
          if (nullness == Nullness.NULLABLE) {
            group(element, map, NullAnalysisResult.NULLS).add(value);
          }
          else if (nullness == Nullness.NOT_NULL) {
            group(element, map, NullAnalysisResult.NOT_NULLS).add(value);
          }
          else {
            Collection<? extends AbstractTreeNode> children = ReadAction.compute(() -> element.getChildren());
            if (children.isEmpty()) {
              group(element, map, NullAnalysisResult.UNKNOWNS).add(value);
            }
            super.visit(element);
          }
        }
      }

      @Override
      public void elementFinished(@NotNull SliceNode element) {
        SliceNode parent = guide.getParent(element);
        if (parent != null) {
          node(parent, map).add(node(element, map));
        }
      }
    };
    walkingState.visit(root);

    return node(root, map);
  }

  @NotNull
  protected abstract Nullness checkNullness(final PsiElement element);

  public static class NullAnalysisResult {
    static final int NULLS = 0;
    static final int NOT_NULLS = 1;
    static final int UNKNOWNS = 2;
    final Collection<PsiElement>[] groupedByValue = new Collection[] {new THashSet<PsiElement>(),new THashSet<PsiElement>(),new THashSet<PsiElement>()};

    public void clear() {
      for (Collection<PsiElement> elements : groupedByValue) {
        elements.clear();
      }
    }

    private void add(NullAnalysisResult duplicate) {
      for (int i = 0; i < groupedByValue.length; i++) {
        Collection<PsiElement> elements = groupedByValue[i];
        Collection<PsiElement> other = duplicate.groupedByValue[i];
        elements.addAll(other);
      }
    }
  }
}
