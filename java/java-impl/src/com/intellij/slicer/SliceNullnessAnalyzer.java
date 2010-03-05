/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: cdr
 */
public class SliceNullnessAnalyzer {
  private static void groupByNullness(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = createNewTree(result, oldRoot, map);

    SliceUsage rootUsage = oldRoot.myCachedChildren.get(0).getValue();
    SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, SliceManager.getElementDescription(null, rootUsage.getElement(), " Grouped by Nullness") );
  }

  public static SliceRootNode createNewTree(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = oldRoot.copy();
    assert oldRoot.myCachedChildren.size() == 1;
    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    root.setChanged();
    root.targetEqualUsages.clear();
    root.myCachedChildren = new ArrayList<SliceNode>();

    createValueRootNode(result, oldRoot, map, root, oldRootStart, "Null Values", NullAnalysisResult.NULLS);
    //if (!result.nulls.isEmpty()) {
    //  SliceLeafValueClassNode nullRoot = new SliceLeafValueClassNode(root.getProject(), root, "Null Values");
    //  root.myCachedChildren.add(nullRoot);
    //
    //
    //  Set<PsiElement> uniqueValues = new THashSet<PsiElement>(result.nulls, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
    //  for (final PsiElement nullExpression : uniqueValues) {
    //    SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, new NullableFunction<SliceNode, SliceNode>() {
    //      public SliceNode fun(SliceNode oldNode) {
    //        if (oldNode.getDuplicate() != null) {
    //          return null;
    //        }
    //
    //        for (PsiElement nullSuspect : node(oldNode, map).nulls) {
    //          if (nullSuspect.getManager().areElementsEquivalent(nullSuspect, nullExpression)) {
    //            return oldNode.copy();
    //          }
    //        }
    //        return null;
    //      }
    //    },new PairProcessor<SliceNode, List<SliceNode>>() {
    //      public boolean process(SliceNode node, List<SliceNode> children) {
    //        if (!children.isEmpty()) return true;
    //        PsiElement element = node.getValue().getElement();
    //        if (element == null) return false;
    //        return element.getManager().areElementsEquivalent(element, nullExpression); // leaf can be there only if it's filtering expression
    //      }
    //    });
    //    nullRoot.myCachedChildren.add(new SliceLeafValueRootNode(root.getProject(), nullExpression, nullRoot, Collections.singletonList(newRoot),
    //                                                             oldRoot.getValue().params));
    //  }
    //}
    createValueRootNode(result, oldRoot, map, root, oldRootStart, "NotNull Values", NullAnalysisResult.NOT_NULLS);
    createValueRootNode(result, oldRoot, map, root, oldRootStart, "Other Values", NullAnalysisResult.UNKNOWNS);

    //if (!result.unknown.isEmpty()) {
    //  SliceLeafValueClassNode valueRoot = new SliceLeafValueClassNode(root.getProject(), root, "Other Values");
    //  root.myCachedChildren.add(valueRoot);
    //
    //  Set<PsiElement> uniqueValues = new THashSet<PsiElement>(result.unknown, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
    //  for (final PsiElement expression : uniqueValues) {
    //    SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, new NullableFunction<SliceNode, SliceNode>() {
    //      public SliceNode fun(SliceNode oldNode) {
    //        return oldNode.getDuplicate() == null && node(oldNode, map).unknown.contains(expression) ? oldNode.copy() : null;
    //      }
    //    },new PairProcessor<SliceNode, List<SliceNode>>() {
    //    public boolean process(SliceNode node, List<SliceNode> children) {
    //      if (!children.isEmpty()) return true;
    //      PsiElement element = node.getValue().getElement();
    //      if (element == null) return false;
    //      return element.getManager().areElementsEquivalent(element, expression); // leaf can be there only if it's filtering expression
    //    }
    //  });
    //    valueRoot.myCachedChildren.add(new SliceLeafValueRootNode(root.getProject(), expression, valueRoot, Collections.singletonList(newRoot),
    //                                                              oldRoot.getValue().params));
    //  }
    //}
    return root;
  }

  private static void createValueRootNode(NullAnalysisResult result, SliceRootNode oldRoot,
                                          final Map<SliceNode, NullAnalysisResult> map,
                                          SliceRootNode root,
                                          SliceNode oldRootStart, String nodeName, final int group) {
    Collection<PsiElement> groupedByValue = result.groupedByValue[group];
    if (groupedByValue.isEmpty()) {
      return;
    }
    SliceLeafValueClassNode valueRoot = new SliceLeafValueClassNode(root.getProject(), root, nodeName);
    root.myCachedChildren.add(valueRoot);

    Set<PsiElement> uniqueValues = new THashSet<PsiElement>(groupedByValue, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
    for (final PsiElement expression : uniqueValues) {
      SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, new NullableFunction<SliceNode, SliceNode>() {
        public SliceNode fun(SliceNode oldNode) {
          if (oldNode.getDuplicate() != null) {
            return null;
          }

          for (PsiElement nullSuspect : group(oldNode, map, group)) {
            if (PsiEquivalenceUtil.areElementsEquivalent(nullSuspect, expression)) {
              return oldNode.copy();
            }
          }
          return null;
        }
      },new PairProcessor<SliceNode, List<SliceNode>>() {
        public boolean process(SliceNode node, List<SliceNode> children) {
          if (!children.isEmpty()) return true;
          PsiElement element = node.getValue().getElement();
          if (element == null) return false;
          return PsiEquivalenceUtil.areElementsEquivalent(element, expression); // leaf can be there only if it's filtering expression
        }
      });
      valueRoot.myCachedChildren.add(new SliceLeafValueRootNode(root.getProject(), expression, valueRoot, Collections.singletonList(newRoot),
                                                                oldRoot.getValue().params));
    }
  }

  public static void startAnalyzeNullness(final AbstractTreeStructure treeStructure, final Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<NullAnalysisResult> leafExpressions = Ref.create(null);
    final Map<SliceNode, NullAnalysisResult> map = createMap();

    ProgressManager.getInstance().run(new Task.Backgroundable(root.getProject(), "Expanding all nodes... (may very well take the whole day)", true) {
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
    return new FactoryMap<SliceNode, NullAnalysisResult>() {
      @Override
      protected NullAnalysisResult create(SliceNode key) {
        return new NullAnalysisResult();
      }

      @Override
      protected Map<SliceNode, NullAnalysisResult> createMap() {
        return new THashMap<SliceNode, NullAnalysisResult>(TObjectHashingStrategy.IDENTITY);
      }
    };
  }

  private static NullAnalysisResult node(SliceNode node, Map<SliceNode, NullAnalysisResult> nulls) {
    return nulls.get(node);
  }
  private static Collection<PsiElement> group(SliceNode node, Map<SliceNode, NullAnalysisResult> nulls, int group) {
    return nulls.get(node).groupedByValue[group];
  }

  @NotNull
  public static NullAnalysisResult calcNullableLeaves(@NotNull final SliceNode root, @NotNull AbstractTreeStructure treeStructure,
                                                       final Map<SliceNode, NullAnalysisResult> map) {
    final SliceLeafAnalyzer.SliceNodeGuide guide = new SliceLeafAnalyzer.SliceNodeGuide(treeStructure);
    WalkingState<SliceNode> walkingState = new WalkingState<SliceNode>(guide) {
      @Override
      public void visit(@NotNull SliceNode element) {
        element.calculateDupNode();
        node(element, map).clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          node(element, map).add(node(duplicate, map));
        }
        else {
          SliceUsage sliceUsage = element.getValue();
          final PsiElement value = sliceUsage.getElement();
          DfaUtil.Nullness nullness = ApplicationManager.getApplication().runReadAction(new Computable<DfaUtil.Nullness>() {
            public DfaUtil.Nullness compute() {
              return checkNullness(value);
            }
          });
          if (nullness == DfaUtil.Nullness.NULL) {
            group(element, map, NullAnalysisResult.NULLS).add(value);
          }
          else if (nullness == DfaUtil.Nullness.NOT_NULL) {
            group(element, map, NullAnalysisResult.NOT_NULLS).add(value);
          }
          else {
            Collection<? extends AbstractTreeNode> children = element.getChildren();
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
  private static DfaUtil.Nullness checkNullness(final PsiElement element) {
    // null
    PsiElement value = element;
    if (value instanceof PsiExpression) {
      value = PsiUtil.deparenthesizeExpression((PsiExpression)value);
    }
    if (value instanceof PsiLiteralExpression) {
      return ((PsiLiteralExpression)value).getValue() == null ? DfaUtil.Nullness.NULL : DfaUtil.Nullness.NOT_NULL;
    }

    // not null
    if (value instanceof PsiNewExpression) return DfaUtil.Nullness.NOT_NULL;
    if (value instanceof PsiThisExpression) return DfaUtil.Nullness.NOT_NULL;
    if (value instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)value).resolveMethod();
      if (method != null && AnnotationUtil.isNotNull(method)) return DfaUtil.Nullness.NOT_NULL;
      if (method != null && AnnotationUtil.isNullable(method)) return DfaUtil.Nullness.NULL;
    }
    if (value instanceof PsiBinaryExpression && ((PsiBinaryExpression)value).getOperationTokenType() == JavaTokenType.PLUS) {
      return DfaUtil.Nullness.NOT_NULL; // "xxx" + var
    }

    // unfortunately have to resolve here, since there can be no subnodes
    PsiElement context = value;
    if (value instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)value).resolve();
      if (resolved instanceof PsiCompiledElement) {
        resolved = resolved.getNavigationElement();
      }
      value = resolved;
    }
    if (value instanceof PsiParameter && ((PsiParameter)value).getDeclarationScope() instanceof PsiCatchSection) {
      // exception thrown is always not null
      return DfaUtil.Nullness.NOT_NULL;
    }

    if (value instanceof PsiLocalVariable || value instanceof PsiParameter) {
      DfaUtil.Nullness result = DfaUtil.checkNullness((PsiVariable)value, context);
      if (result != DfaUtil.Nullness.UNKNOWN) {
        return result;
      }
    }

    if (value instanceof PsiModifierListOwner) {
      if (AnnotationUtil.isNotNull((PsiModifierListOwner)value)) return DfaUtil.Nullness.NOT_NULL;
      if (AnnotationUtil.isNullable((PsiModifierListOwner)value)) return DfaUtil.Nullness.NULL;
    }

    if (value instanceof PsiEnumConstant) return DfaUtil.Nullness.NOT_NULL;
    return DfaUtil.Nullness.UNKNOWN;
  }

  public static class NullAnalysisResult {
    public static int NULLS = 0;
    public static int NOT_NULLS = 1;
    public static int UNKNOWNS = 2;
    public final Collection<PsiElement>[] groupedByValue = new Collection[] {new THashSet<PsiElement>(),new THashSet<PsiElement>(),new THashSet<PsiElement>()};

    public void clear() {
      for (Collection<PsiElement> elements : groupedByValue) {
        elements.clear();
      }
    }

    public void add(NullAnalysisResult duplicate) {
      for (int i = 0; i < groupedByValue.length; i++) {
        Collection<PsiElement> elements = groupedByValue[i];
        Collection<PsiElement> other = duplicate.groupedByValue[i];
        elements.addAll(other);
      }
    }
  }
}
