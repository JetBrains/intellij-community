package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class TreeElement extends ElementBase implements ASTNode, Cloneable {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  public volatile TreeElement next = null;
  public volatile TreeElement prev = null;
  public volatile CompositeElement parent = null;

  public Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.clearCaches();

    clone.next = null;
    clone.prev = null;
    clone.parent = null;

    return clone;
  }

  public ASTNode copyElement() {
    CharTable table = SharedImplUtil.findCharTableByTree(this);
    return ChangeUtil.copyElement(this, table);
  }

  public PsiManagerEx getManager() {
    TreeElement element;
    for (element = this; element.getTreeParent() != null; element = element.getTreeParent()) {
    }

    if (element instanceof FileElement) { //TODO!!
      return element.getManager();
    }
    else {
      if (getTreeParent() != null) {
        return getTreeParent().getManager();
      }
      return null;
    }
  }

  public abstract LeafElement findLeafElementAt(int offset);

  @NotNull
  public abstract char[] textToCharArray();

  public abstract TreeElement getFirstChildNode();

  public abstract TreeElement getLastChildNode();

  public TextRange getTextRange() {
    int start = getStartOffset();
    return new TextRange(start, start + getTextLength());
  }

  public int getStartOffset() {
    if (parent == null) return 0;
    int offset = parent.getStartOffset();
    for (TreeElement element1 = parent.firstChild; element1 != this; element1 = element1.next) {
      offset += element1.getTextLength();
    }
    return offset;
  }

  public final int getStartOffsetInParent() {
    if (parent == null) return -1;
    int offset = 0;
    for (TreeElement child = parent.firstChild; child != this; child = child.next) {
      offset += child.getTextLength();
    }
    return offset;
  }

  public int getTextOffset() {
    return getStartOffset();
  }

  public boolean textMatches(CharSequence buffer, int startOffset, int endOffset) {
    return textMatches(this, buffer, startOffset) == endOffset;
  }

  private static int textMatches(ASTNode element, CharSequence buffer, int startOffset) {
    if (element instanceof LeafElement) {
      final LeafElement leaf = (LeafElement)element;
      return leaf.textMatches(buffer, startOffset);
    }
    else {
      int curOffset = startOffset;
      for (TreeElement child = ((CompositeElement)element).firstChild; child != null; child = child.next) {
        curOffset = textMatches(child, buffer, curOffset);
        if (curOffset == -1) return -1;
      }
      return curOffset;
    }
  }

  public boolean textMatches(@NotNull CharSequence seq) {
    return textMatches(seq, 0, seq.length());
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getTextLength() == element.getTextLength() && textMatches(element.getText());
  }

  @NonNls
  public String toString() {
    return "Element" + "(" + getElementType().toString() + ")";
  }

  public final CompositeElement getTreeParent() {
    return parent;
    /*
    if (isLast()) return (CompositeElement)next;

    TreeElement next = this.next;
    while (next instanceof LeafElement && !((LeafElement)next).isLast()) next = next.next;
    if (next != null) return next.getTreeParent();
    return null;
    */
  }

  public final TreeElement getTreePrev() {
    return prev;
    /*
    final CompositeElement parent = getTreeParent();
    if (parent == null) return null;
    TreeElement firstChild = parent.firstChild;
    if (firstChild == this) return null;
    while (firstChild != null && firstChild.next != this) firstChild = firstChild.next;
    return firstChild;
    */
  }

  public final void setTreeParent(CompositeElement parent) {
    this.parent = parent;
    /*
    if (next == null || isLast()) {
      next = parent;
      setLast(true);
    }
    */
  }

  public final void setTreePrev(TreeElement prev) {
    this.prev = prev;
  }

  public final TreeElement getTreeNext() {
    return next;
  }

  public final void setTreeNext(TreeElement next) {
    this.next = next;
  }

  public void clearCaches() { }

  public final boolean equals(Object obj) {
    return obj == this;
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public abstract int hc(); // Used in tree diffing

  public ASTNode getTransformedFirstOrSelf() {
    return this;
  }

  public ASTNode getTransformedLastOrSelf() {
    return this;
  }

  public abstract void acceptTree(TreeElementVisitor visitor);

  public void onInvalidated() {
    final Boolean trackInvalidation = getUserData(DebugUtil.TRACK_INVALIDATION);
    if (trackInvalidation != null && trackInvalidation) {
      //noinspection HardCodedStringLiteral
      new Throwable("Element invalidated:" + this).printStackTrace();
    }
  }

}

