package com.intellij.dupLocator;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AbstractMatchingVisitor {

  public abstract boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2);

  public abstract boolean match(PsiElement element1, PsiElement element2);

  protected abstract boolean doMatchInAnyOrder(NodeIterator elements, NodeIterator elements2);

  public boolean matchSequentially(@NotNull PsiElement[] elements1, @NotNull PsiElement[] element2) {
    return matchSequentially(new FilteringNodeIterator(new ArrayBackedNodeIterator(elements1), getNodeFilter()),
                             new FilteringNodeIterator(new ArrayBackedNodeIterator(element2), getNodeFilter()));
  }

  @NotNull
  protected abstract NodeFilter getNodeFilter();

  public boolean matchOptionally(@Nullable PsiElement element1, @Nullable PsiElement element2) {
    return element1 == null && isLeftLooseMatching() ||
           element2 == null && isRightLooseMatching() ||
           match(element1, element2);
  }

  public boolean matchSons(final PsiElement el1, final PsiElement el2) {
    if (el1 == null || el2 == null) return el1 == el2;
    return matchSequentially(el1.getFirstChild(), el2.getFirstChild());
  }

  public boolean matchSonsOptionally(final PsiElement element, final PsiElement element2) {
    if (element == null && isLeftLooseMatching()) {
      return true;
    }
    if (element2 == null && isRightLooseMatching()) {
      return true;
    }
    if (element == null || element2 == null) {
      return element == element2;
    }
    return matchSequentiallyOptionally(element.getFirstChild(), element2.getFirstChild());
  }

  public final boolean matchSonsInAnyOrder(PsiElement element1, PsiElement element2) {
    if (element1 == null && isLeftLooseMatching()) {
      return true;
    }
    if (element2 == null && isRightLooseMatching()) {
      return true;
    }
    if (element1 == null || element2 == null) {
      return element1 == element2;
    }
    PsiElement e = element1.getFirstChild();
    PsiElement e2 = element2.getFirstChild();
    return (e == null && isLeftLooseMatching()) ||
           (e2 == null && isRightLooseMatching()) ||
           matchInAnyOrder(new FilteringNodeIterator(new SiblingNodeIterator(e), getNodeFilter()),
                           new FilteringNodeIterator(new SiblingNodeIterator(e2), getNodeFilter()));
  }

  public boolean matchOptionally(@NotNull PsiElement[] elements1, @NotNull PsiElement[] elements2) {
    return (elements1.length == 0 && isLeftLooseMatching()) ||
           (elements2.length == 0 && isRightLooseMatching()) ||
           matchSequentially(elements1, elements2);
  }

  public final boolean matchInAnyOrder(final PsiElement[] elements, final PsiElement[] elements2) {
    if (elements == elements2) return true;

    return matchInAnyOrder(
      new ArrayBackedNodeIterator(elements),
      new ArrayBackedNodeIterator(elements2)
    );
  }

  protected boolean isLeftLooseMatching() {
    return true;
  }

  protected boolean isRightLooseMatching() {
    return true;
  }

  public boolean matchSequentially(PsiElement el1, PsiElement el2) {
    //if (el1==null || el2==null) return el1 == el2;
    return matchSequentially(new FilteringNodeIterator(new SiblingNodeIterator(el1), getNodeFilter()),
                             new FilteringNodeIterator(new SiblingNodeIterator(el2), getNodeFilter()));
  }

  public boolean matchSequentiallyOptionally(PsiElement el1, PsiElement el2) {
    return (el1 == null && isLeftLooseMatching()) ||
           (el2 == null && isRightLooseMatching()) ||
           matchSequentially(new FilteringNodeIterator(new SiblingNodeIterator(el1), getNodeFilter()),
                             new FilteringNodeIterator(new SiblingNodeIterator(el2), getNodeFilter()));
  }

  public final boolean matchInAnyOrder(final NodeIterator elements, final NodeIterator elements2) {
    if ((!elements.hasNext() && isLeftLooseMatching()) ||
        (!elements2.hasNext() && isRightLooseMatching()) ||
        (!elements.hasNext() && !elements2.hasNext())
      ) {
      return true;
    }

    return doMatchInAnyOrder(elements, elements2);
  }
}
