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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.WalkingState;
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class SliceLeafAnalyzer {
  public static final TObjectHashingStrategy<PsiElement> LEAF_ELEMENT_EQUALITY = new TObjectHashingStrategy<PsiElement>() {
    public int computeHashCode(final PsiElement element) {
      if (element == null) return 0;
      String text = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          PsiElement elementToCompare = element;
          if (element instanceof PsiJavaReference) {
            PsiElement resolved = ((PsiJavaReference)element).resolve();
            if (resolved != null) {
              elementToCompare = resolved;
            }
          }
          return elementToCompare instanceof PsiNamedElement ?
                 ((PsiNamedElement)elementToCompare).getName() : SourceUtil.getTextSkipWhiteSpaceAndComments(elementToCompare.getNode());
        }
      });
      return Comparing.hashcode(text);
    }

    public boolean equals(final PsiElement o1, final PsiElement o2) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return o1 != null && o2 != null && PsiEquivalenceUtil.areElementsEquivalent(o1, o2);
        }
      });
    }
  };

  static SliceNode filterTree(SliceNode oldRoot, NullableFunction<SliceNode, SliceNode> filter, PairProcessor<SliceNode, List<SliceNode>> postProcessor){
    SliceNode filtered = filter.fun(oldRoot);
    if (filtered == null) return null;

    List<SliceNode> childrenFiltered = new ArrayList<SliceNode>();
    if (oldRoot.myCachedChildren != null) {
      for (SliceNode child : oldRoot.myCachedChildren) {
        SliceNode childFiltered = filterTree(child, filter,postProcessor);
        if (childFiltered != null) {
          childrenFiltered.add(childFiltered);
        }
      }
    }
    boolean success = postProcessor == null || postProcessor.process(filtered, childrenFiltered);
    if (!success) return null;
    filtered.myCachedChildren = new ArrayList<SliceNode>(childrenFiltered);
    return filtered;
  }

  private static void groupByValues(Collection<PsiElement> leaves, SliceRootNode oldRoot, final Map<SliceNode, Collection<PsiElement>> map) {
    assert oldRoot.myCachedChildren.size() == 1;
    SliceRootNode root = createTreeGroupedByValues(leaves, oldRoot, map);

    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceUsage rootUsage = oldRootStart.getValue();
    SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, SliceManager.getElementDescription(null, rootUsage.getElement(), " Grouped by Value") );
  }

  public static SliceRootNode createTreeGroupedByValues(Collection<PsiElement> leaves, SliceRootNode oldRoot, final Map<SliceNode, Collection<PsiElement>> map) {
    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceRootNode root = oldRoot.copy();
    root.setChanged();
    root.targetEqualUsages.clear();
    root.myCachedChildren = new ArrayList<SliceNode>(leaves.size());

    for (final PsiElement leafExpression : leaves) {
      SliceNode newNode = filterTree(oldRootStart, new NullableFunction<SliceNode, SliceNode>() {
        public SliceNode fun(SliceNode oldNode) {
          if (oldNode.getDuplicate() != null) return null;
          if (!node(oldNode, map).contains(leafExpression)) return null;

          return oldNode.copy();
        }
      }, new PairProcessor<SliceNode, List<SliceNode>>() {
        public boolean process(SliceNode node, List<SliceNode> children) {
          if (!children.isEmpty()) return true;
          PsiElement element = node.getValue().getElement();
          if (element == null) return false;
          return element.getManager().areElementsEquivalent(element, leafExpression); // leaf can be there only if it's filtering expression
        }
      });

      SliceLeafValueRootNode lvNode = new SliceLeafValueRootNode(root.getProject(), leafExpression, root, Collections.singletonList(newNode),
                                                                 oldRoot.getValue().params);
      root.myCachedChildren.add(lvNode);
    }
    return root;
  }

  public static void startAnalyzeValues(final AbstractTreeStructure treeStructure, final Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<Collection<PsiElement>> leafExpressions = Ref.create(null);

    final Map<SliceNode, Collection<PsiElement>> map = createMap();

    ProgressManager.getInstance().run(new Task.Backgroundable(root.getProject(), "Expanding all nodes... (may very well take the whole day)", true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        Collection<PsiElement> l = calcLeafExpressions(root, treeStructure, map);
        leafExpressions.set(l);
      }

      @Override
      public void onCancel() {
        finish.run();
      }

      @Override
      public void onSuccess() {
        try {
          Collection<PsiElement> leaves = leafExpressions.get();
          if (leaves == null) return;  //cancelled

          if (leaves.isEmpty()) {
            Messages.showErrorDialog("Unable to find leaf expressions to group by", "Cannot group");
            return;
          }

          groupByValues(leaves, root, map);
        }
        finally {
          finish.run();
        }
      }
    });

  }

  public static Map<SliceNode, Collection<PsiElement>> createMap() {
    return new FactoryMap<SliceNode, Collection<PsiElement>>() {
      @Override
      protected Map<SliceNode, Collection<PsiElement>> createMap() {
        return new ConcurrentHashMap<SliceNode, Collection<PsiElement>>(TObjectHashingStrategy.IDENTITY);
      }

      @Override
      protected Collection<PsiElement> create(SliceNode key) {
        return new THashSet<PsiElement>(SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
      }
    };
  }

  static class SliceNodeGuide implements WalkingState.TreeGuide<SliceNode> {
    private final AbstractTreeStructure myTreeStructure;
    // use tree structure because it's setting 'parent' fields in the process

    SliceNodeGuide(@NotNull AbstractTreeStructure treeStructure) {
      myTreeStructure = treeStructure;
    }

    public SliceNode getNextSibling(@NotNull SliceNode element) {
      AbstractTreeNode parent = element.getParent();
      if (parent == null) return null;

      return element.getNext((List)parent.getChildren());
    }

    public SliceNode getPrevSibling(@NotNull SliceNode element) {
      AbstractTreeNode parent = element.getParent();
      if (parent == null) return null;
      return element.getPrev((List)parent.getChildren());
    }

    public SliceNode getFirstChild(@NotNull SliceNode element) {
      Object[] children = myTreeStructure.getChildElements(element);
      return children.length == 0 ? null : (SliceNode)children[0];
    }

    public SliceNode getParent(@NotNull SliceNode element) {
      AbstractTreeNode parent = element.getParent();
      return parent instanceof SliceNode ? (SliceNode)parent : null;
    }
  }

  private static Collection<PsiElement> node(SliceNode node, Map<SliceNode, Collection<PsiElement>> map) {
    return map.get(node);
  }

  @NotNull
  public static Collection<PsiElement> calcLeafExpressions(@NotNull final SliceNode root, AbstractTreeStructure treeStructure,
                                                           final Map<SliceNode, Collection<PsiElement>> map) {
    final SliceNodeGuide guide = new SliceNodeGuide(treeStructure);
    WalkingState<SliceNode> walkingState = new WalkingState<SliceNode>(guide) {
      @Override
      public void visit(@NotNull SliceNode element) {
        element.calculateDupNode();
        node(element, map).clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          node(element, map).addAll(node(duplicate, map));
        }
        else {
          SliceUsage sliceUsage = element.getValue();

          Collection<? extends AbstractTreeNode> children = element.getChildren();
          if (children.isEmpty()) {
            PsiElement value = sliceUsage.getElement();
            node(element, map).addAll(ContainerUtil.singleton(value, LEAF_ELEMENT_EQUALITY));
          }
          super.visit(element);
        }
      }

      @Override
      public void elementFinished(@NotNull SliceNode element) {
        SliceNode parent = guide.getParent(element);
        if (parent != null) {
          node(parent, map).addAll(node(element, map));
        }
      }
    };
    walkingState.visit(root);

    return node(root, map);
  }
}
