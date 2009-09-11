package com.intellij.slicer;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.WalkingState;
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public class SliceLeafAnalyzer {
  public static final TObjectHashingStrategy<PsiElement> LEAF_ELEMENT_EQUALITY = new TObjectHashingStrategy<PsiElement>() {
    public int computeHashCode(PsiElement element) {
      if (element == null) return 0;
      PsiElement o = elementToCompare(element);
      String text = o instanceof PsiNamedElement ? ((PsiNamedElement)o).getName() : SourceUtil.getTextSkipWhiteSpaceAndComments(o.getNode());
      return Comparing.hashcode(text);
    }

    @NotNull
    private PsiElement elementToCompare(PsiElement element) {
      if (element instanceof PsiJavaReference) {
        PsiElement resolved = ((PsiJavaReference)element).resolve();
        if (resolved != null) return resolved;
      }
      return element;
    }

    public boolean equals(PsiElement o1, PsiElement o2) {
      return o1 != null && o2 != null && PsiEquivalenceUtil.areElementsEquivalent(o1, o2);
    }
  };

  private static class SliceNodeGuide implements WalkingState.TreeGuide<SliceNode> {
    public SliceNode getNextSibling(SliceNode element) {
      return element.getNext();
    }

    public SliceNode getPrevSibling(SliceNode element) {
      return element.getPrev();
    }

    public SliceNode getFirstChild(SliceNode element) {
      Object[] children = element.getTreeBuilder().getTreeStructure().getChildElements(element);
      return children.length == 0 ? null : (SliceNode)children[0];
    }

    public SliceNode getParent(SliceNode element) {
      AbstractTreeNode parent = element.getParent();
      return parent instanceof SliceNode ? (SliceNode)parent : null;
    }
    private static final SliceNodeGuide instance = new SliceNodeGuide();
  }

  @NotNull
  public static Collection<PsiElement> calcLeafExpressions(@NotNull final SliceNode root) {
    WalkingState<SliceNode> walkingState = new WalkingState<SliceNode>(SliceNodeGuide.instance) {
      @Override
      public void visit(SliceNode element) {
        element.update(null);
        element.getLeafExpressions().clear();
        SliceNode duplicate = element.getDuplicate();
        if (duplicate != null) {
          element.addLeafExpressions(duplicate.getLeafExpressions());
        }
        else {
          SliceUsage sliceUsage = element.getValue();

          Object[] children = element.getTreeBuilder().getTreeStructure().getChildElements(element);
          if (children.length == 0) {
            PsiElement value = sliceUsage.getElement();
            element.addLeafExpressions(ContainerUtil.singleton(value, LEAF_ELEMENT_EQUALITY));
          }
          super.visit(element);
        }
      }

      @Override
      public void elementFinished(SliceNode element) {
        SliceNode parent = SliceNodeGuide.instance.getParent(element);
        if (parent != null) {
          parent.addLeafExpressions(element.getLeafExpressions());
        }
      }
    };
    walkingState.elementStarted(root);

    return root.getLeafExpressions();
  }

  //@NotNull
  //public static Collection<PsiElement> calcLeafExpressionsSOE(@NotNull SliceNode root, @NotNull ProgressIndicator progress) {
  //  root.update(null);
  //  root.getLeafExpressions().clear();
  //  Collection<PsiElement> leaves;
  //  SliceNode duplicate = root.getDuplicate();
  //  if (duplicate != null) {
  //    leaves = duplicate.getLeafExpressions();
  //    //null means other
  //    //leaves = ContainerUtil.singleton(PsiUtilBase.NULL_PSI_ELEMENT, LEAF_ELEMENT_EQUALITY);
  //    //return leaves;//todo
  //  }
  //  else {
  //    SliceUsage sliceUsage = root.getValue();
  //
  //    Collection<? extends AbstractTreeNode> children = root.getChildrenUnderProgress(progress);
  //    if (children.isEmpty()) {
  //      PsiElement element = sliceUsage.getElement();
  //      leaves = ContainerUtil.singleton(element, LEAF_ELEMENT_EQUALITY);
  //    }
  //    else {
  //      leaves = new THashSet<PsiElement>(LEAF_ELEMENT_EQUALITY);
  //      for (AbstractTreeNode child : children) {
  //        Collection<PsiElement> elements = calcLeafExpressions((SliceNode)child);
  //        leaves.addAll(elements);
  //      }
  //    }
  //  }
  //
  //  root.addLeafExpressions(leaves);
  //  return leaves;
  //}
}
