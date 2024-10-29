// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairProcessor;
import com.intellij.util.WalkingState;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SliceLeafAnalyzer {
  private static final Logger LOG = Logger.getInstance(SliceLeafAnalyzer.class);

  private final @NotNull SliceLeafEquality myLeafEquality;

  private final @NotNull SliceLanguageSupportProvider myProvider;

  public SliceLeafAnalyzer(@NotNull SliceLeafEquality leafEquality, @NotNull SliceLanguageSupportProvider provider) {
    myLeafEquality = leafEquality;
    myProvider = provider;
  }

  public static SliceNode filterTree(SliceNode oldRoot,
                                     NullableFunction<? super SliceNode, ? extends SliceNode> filter,
                                     PairProcessor<? super SliceNode, ? super List<SliceNode>> postProcessor) {
    SliceNode filtered = filter.fun(oldRoot);
    if (filtered == null) return null;

    List<SliceNode> childrenFiltered = new ArrayList<>();
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
    filtered.myCachedChildren = new ArrayList<>(childrenFiltered);
    return filtered;
  }

  private void groupByValues(@NotNull Collection<? extends PsiElement> leaves,
                                    @NotNull SliceRootNode oldRoot,
                                    @NotNull Map<SliceNode, Collection<PsiElement>> map) {
    assert oldRoot.myCachedChildren.size() == 1;
    SliceRootNode root = createTreeGroupedByValues(leaves, oldRoot, map);

    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceUsage rootUsage = oldRootStart.getValue();
    String description = SliceManager.getElementDescription(null, rootUsage.getElement(), LangBundle.message("tab.title.grouped.by.value"));
    SliceManager.getInstance(root.getProject()).createToolWindow(true, root, true, description);
  }

  public @NotNull SliceRootNode createTreeGroupedByValues(@NotNull Collection<? extends PsiElement> leaves,
                                                          @NotNull SliceRootNode oldRoot,
                                                          @NotNull Map<SliceNode, Collection<PsiElement>> map) {
    SliceNode oldRootStart = oldRoot.myCachedChildren.get(0);
    SliceRootNode root = oldRoot.copy();
    root.setChanged();
    root.targetEqualUsages.clear();
    List<SliceNode> leafValueRoots = new ArrayList<>(leaves.size());

    for (final PsiElement leafExpression : leaves) {
      SliceNode newNode = filterTree(oldRootStart, oldNode -> {
        if (oldNode.getDuplicate() != null) return null;
        if (!node(oldNode, map).contains(leafExpression)) return null;

        return oldNode.copy();
      }, (node, children) -> {
        if (!children.isEmpty()) return true;
        PsiElement element = node.getValue().getElement();
        if (element == null) return false;
        return element.getManager().areElementsEquivalent(element, leafExpression); // leaf can be there only if it's filtering expression
      });

      SliceLeafValueRootNode lvNode = new SliceLeafValueRootNode(root.getProject(),
                                                                 root,
                                                                 myProvider.createRootUsage(leafExpression, oldRoot.getValue().params),
                                                                 Collections.singletonList(newNode));
      leafValueRoots.add(lvNode);
    }
    root.setChildren(leafValueRoots);

    return root;
  }

  public void startAnalyzeValues(final @NotNull AbstractTreeStructure treeStructure, final @NotNull Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<Collection<PsiElement>> leafExpressions = Ref.create(null);

    final Map<SliceNode, Collection<PsiElement>> map = createMap();

    String encouragementPiece = " " + LangBundle.message("progress.title.may.very.well.take.whole.day");
    ProgressManager.getInstance().run(new Task.Backgroundable(
      root.getProject(), LangBundle.message("progress.title.expanding.all.nodes", encouragementPiece), true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
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
            Messages.showErrorDialog(LangBundle.message("dialog.message.unable.to.find.leaf.expressions.to.group.by"),
                                     LangBundle.message("dialog.title.cannot.group"));
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

  public Map<SliceNode, Collection<PsiElement>> createMap() {
    Function<SliceNode, Collection<PsiElement>> function = k -> {
      return ConcurrentCollectionFactory.createConcurrentSet(new HashingStrategy<>() {
        @Override
        public int hashCode(PsiElement object) {
          return myLeafEquality.hashCode(object);
        }

        @Override
        public boolean equals(PsiElement o1, PsiElement o2) {
          return myLeafEquality.equals(o1, o2);
        }
      });
    };
    return ConcurrentFactoryMap.create(function, ConcurrentCollectionFactory::createConcurrentIdentityMap);
  }

  public static final class SliceNodeGuide implements WalkingState.TreeGuide<SliceNode> {
    private final AbstractTreeStructure myTreeStructure;
    // use tree structure because it's setting 'parent' fields in the process

    public SliceNodeGuide(@NotNull AbstractTreeStructure treeStructure) {
      myTreeStructure = treeStructure;
    }

    @Override
    public SliceNode getNextSibling(@NotNull SliceNode element) {
      AbstractTreeNode<?> parent = element.getParent();
      if (parent == null) return null;

      return element.getNext((List<? extends AbstractTreeNode<?>>)parent.getChildren());
    }

    @Override
    public SliceNode getPrevSibling(@NotNull SliceNode element) {
      AbstractTreeNode<?> parent = element.getParent();
      if (parent == null) return null;
      return element.getPrev((List<? extends AbstractTreeNode<?>>)parent.getChildren());
    }

    @Override
    public SliceNode getFirstChild(@NotNull SliceNode element) {
      Object[] children = myTreeStructure.getChildElements(element);
      return children.length == 0 ? null : (SliceNode)children[0];
    }

    @Override
    public SliceNode getParent(@NotNull SliceNode element) {
      AbstractTreeNode<?> parent = element.getParent();
      return parent instanceof SliceNode ? (SliceNode)parent : null;
    }
  }

  private static Collection<PsiElement> node(@NotNull SliceNode node, @NotNull Map<SliceNode, Collection<PsiElement>> map) {
    return map.get(node);
  }

  public @NotNull Collection<PsiElement> calcLeafExpressions(final @NotNull SliceNode root,
                                                             @NotNull AbstractTreeStructure treeStructure,
                                                             final @NotNull Map<SliceNode, Collection<PsiElement>> map) {
    final SliceNodeGuide guide = new SliceNodeGuide(treeStructure);
    AtomicInteger depth = new AtomicInteger();
    boolean printToLog = LOG.isTraceEnabled();
    WalkingState<SliceNode> walkingState = new WalkingState<>(guide) {
      @Override
      public void elementStarted(@NotNull SliceNode element) {
        depth.incrementAndGet();
        super.elementStarted(element);
      }

      @Override
      public void visit(final @NotNull SliceNode element) {
        element.calculateDupNode();
        node(element, map).clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          node(element, map).addAll(node(duplicate, map));
        }
        else {
          ApplicationManager.getApplication().runReadAction(() -> {
            final SliceUsage sliceUsage = element.getValue();

            Collection<SliceNode> children = element.getChildren();
            if (printToLog) {
              LOG.trace(StringUtil.repeat("  ", Math.max(depth.get(), 0)) + "analyzing usages of " + sliceUsage +
                        " (in " + (sliceUsage == null ? "null" : sliceUsage.getFile().getName() + ":" + sliceUsage.getLine()) + ")");
            }
            if (children.isEmpty() && sliceUsage != null && sliceUsage.canBeLeaf()) {
              PsiElement value = sliceUsage.getElement();
              if (value != null) {
                node(element, map).add(value);
              }
            }
          });

          super.visit(element);
        }
      }

      @Override
      public void elementFinished(@NotNull SliceNode element) {
        depth.decrementAndGet();
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
