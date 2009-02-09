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
  private volatile TreeElement next = null;
  private volatile TreeElement prev = null;
  private volatile CompositeElement parent = null;

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
    for (TreeElement element1 = parent.getFirstChildNode(); element1 != this; element1 = element1.next) {
      offset += element1.getTextLength();
    }
    return offset;
  }

  public final int getStartOffsetInParent() {
    if (parent == null) return -1;
    int offset = 0;
    for (TreeElement child = parent.getFirstChildNode(); child != this; child = child.next) {
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
      for (TreeElement child = ((CompositeElement)element).getFirstChildNode(); child != null; child = child.next) {
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
  }

  public final TreeElement getTreePrev() {
    return prev;
  }

  public final void setTreeParent(CompositeElement parent) {
    this.parent = parent;
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

  protected void onInvalidated() {
    if (DebugUtil.TRACK_INVALIDATION) {
      final Boolean trackInvalidation = getUserData(DebugUtil.TRACK_INVALIDATION_KEY);
      if (trackInvalidation != null && trackInvalidation) {
        //noinspection HardCodedStringLiteral
        new Throwable("Element invalidated:" + this).printStackTrace();
      }
    }
  }

  public void rawInsertBeforeMe(@NotNull TreeElement firstNew) {
    final TreeElement anchorPrev = getTreePrev();
    if(anchorPrev == null){
      firstNew.rawRemoveUpToLast();
      final CompositeElement p = getTreeParent();
      if(p != null) p.setFirstChildNode(firstNew);
      while(true){
        final TreeElement treeNext = firstNew.getTreeNext();
        assert treeNext != this : "Attempt to create cycle";
        firstNew.setTreeParent(p);
        if(treeNext == null) break;
        firstNew = treeNext;
      }
      setTreePrev(firstNew);
      firstNew.setTreeNext(this);
    }
    else anchorPrev.rawInsertAfterMe(firstNew);

    if (DebugUtil.CHECK){
      DebugUtil.checkTreeStructure(this);
    }
  }

  public void rawInsertAfterMe(@NotNull TreeElement firstNew) {
    firstNew.rawRemoveUpToLast();
    final CompositeElement p = getTreeParent();
    final TreeElement treeNext = getTreeNext();
    firstNew.setTreePrev(this);
    setTreeNext(firstNew);
    while(true){
      final TreeElement n = firstNew.getTreeNext();
      assert n != this : "Attempt to create cycle";
      firstNew.setTreeParent(p);
      if(n == null) break;
      firstNew = n;
    }

    if(treeNext == null){
      if(p != null){
        firstNew.setTreeParent(p);
        p.setLastChildNode(firstNew);
      }
    }
    else{
      firstNew.setTreeNext(treeNext);
      treeNext.setTreePrev(firstNew);
    }
    if (DebugUtil.CHECK){
      DebugUtil.checkTreeStructure(this);
    }
  }

  public void rawRemove() {
    final TreeElement nxt = getTreeNext();
    final CompositeElement p = getTreeParent();
    final TreeElement prv = getTreePrev();

    if(prv != null){
      prv.setTreeNext(nxt);
    }
    else if(p != null) {
      p.setFirstChildNode(nxt);
    }

    if(nxt != null){
      nxt.setTreePrev(prv);
    }
    else if(p != null) {
      p.setLastChildNode(prv);
    }

    if (DebugUtil.CHECK){
      if (getTreeParent() != null){
        DebugUtil.checkTreeStructure(getTreeParent());
      }
      if (getTreePrev() != null){
        DebugUtil.checkTreeStructure(getTreePrev());
      }
      if (getTreeNext() != null){
        DebugUtil.checkTreeStructure(getTreeNext());
      }
    }

    invalidate();
  }

  public void rawReplaceWithList(TreeElement firstNew) {
    if (firstNew != null){
      rawInsertAfterMe(firstNew);
    }
    rawRemove();
  }

  protected void invalidate() {
    // invalidate replaced element
    setTreeNext(null);
    setTreePrev(null);
    setTreeParent(null);
    onInvalidated();
  }

  public void rawRemoveUpToLast() {
    rawRemoveUpTo(null);
  }

  // remove nodes from start[including] to end[excluding] from the parent
  public void rawRemoveUpTo(TreeElement end) {
    if(this == end) return;

    final CompositeElement prnt = getTreeParent();
    final TreeElement startPrev = getTreePrev();
    final TreeElement endPrev = end != null ? end.getTreePrev() : null;

    assert end == null || end.getTreeParent() == prnt : "Trying to remove non-child";

    if (prnt != null){
      if (this == prnt.getFirstChildNode()) {
        prnt.setFirstChildNode(end);
      }
      if (end == null) {
        prnt.setLastChildNode(startPrev);
      }
    }
    if (startPrev != null){
      startPrev.setTreeNext(end);
    }
    if (end != null){
      end.setTreePrev(startPrev);
    }

    setTreePrev(null);
    if (endPrev != null){
      endPrev.setTreeNext(null);
    }

    if (prnt != null){
      for(TreeElement element = this; element != null; element = element.getTreeNext()){
        element.setTreeParent(null);
        element.onInvalidated();
      }
    }

    if (DebugUtil.CHECK){
      if (prnt != null){
        DebugUtil.checkTreeStructure(prnt);
      }
      DebugUtil.checkTreeStructure(this);
    }
  }
}

