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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class TreeElement extends ElementBase implements ASTNode, Cloneable {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  private TreeElement myNextSibling = null;
  private TreeElement myPrevSibling = null;
  private CompositeElement myParent = null;

  private final IElementType myType;
  private volatile int myStartOffsetInParent = -1;
  @NonNls protected static final String START_OFFSET_LOCK = new String("TreeElement.START_OFFSET_LOCK");

  public TreeElement(IElementType type) {
    myType = type;
  }

  public Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.clearCaches();

    clone.myNextSibling = null;
    clone.myPrevSibling = null;
    clone.myParent = null;
    clone.myStartOffsetInParent = -1;

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

  public abstract int getNotCachedLength();

  public abstract int getCachedLength();

  public TextRange getTextRange() {
    int start = getStartOffset();
    return new TextRange(start, start + getTextLength());
  }

  public int getStartOffset() {
    int result = 0;
    TreeElement current = this;
    while(current.myParent != null) {
      result += current.getStartOffsetInParent();
      current = current.myParent;
    }
    
    return result;
  }

  public final int getStartOffsetInParent() {
    if (myParent == null) return -1;
    int offsetInParent = myStartOffsetInParent;
    if (offsetInParent != -1) return offsetInParent;
    
    synchronized (START_OFFSET_LOCK) {
      TreeElement cur = this;
      offsetInParent = myStartOffsetInParent;
      if (offsetInParent != -1) return offsetInParent;

      while (true) {
        TreeElement prev = cur.getTreePrev();
        if (prev == null) break;
        cur = prev;
        offsetInParent = cur.myStartOffsetInParent;
        if (offsetInParent != -1) break;
      }

      if (offsetInParent == -1) {
        cur.myStartOffsetInParent = offsetInParent = 0;
      }

      while (cur != this) {
        TreeElement next = cur.getTreeNext();
        offsetInParent += cur.getTextLength();
        next.myStartOffsetInParent = offsetInParent;
        cur = next;
      }
      return offsetInParent;
    }
  }

  public int getTextOffset() {
    return getStartOffset();
  }

  public boolean textMatches(CharSequence buffer, int startOffset, int endOffset) {
    return textMatches(buffer, startOffset) == endOffset;
  }

  protected abstract int textMatches(CharSequence buffer, int start);

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
    return myParent;
  }

  public final TreeElement getTreePrev() {
    return myPrevSibling;
  }

  public final void setTreeParent(CompositeElement parent) {
    myParent = parent;
  }

  public final void setTreePrev(TreeElement prev) {
    myPrevSibling = prev;
    clearRelativeOffsets(this);
  }

  public final TreeElement getTreeNext() {
    return myNextSibling;
  }

  public final void setTreeNext(TreeElement next) {
    myNextSibling = next;
    clearRelativeOffsets(next);
  }

  protected static void clearRelativeOffsets(TreeElement element) {
    TreeElement cur = element;
    while (cur != null && cur.myStartOffsetInParent != -1) {
      cur.myStartOffsetInParent = -1;
      cur = cur.getTreeNext();
    }
  }

  public void clearCaches() {
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public final boolean equals(Object obj) {
    return obj == this;
  }

  public abstract int hc(); // Used in tree diffing

  public abstract void acceptTree(TreeElementVisitor visitor);

  protected void onInvalidated() {
    if (DebugUtil.shouldTrackInvalidation()) {
      final Boolean trackInvalidation = getUserData(DebugUtil.TRACK_INVALIDATION_KEY);
      if (trackInvalidation != null && trackInvalidation) {
        //noinspection HardCodedStringLiteral,ThrowableInstanceNeverThrown,CallToPrintStackTrace
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

  // remove nodes from this[including] to end[excluding] from the parent
  public void rawRemoveUpTo(TreeElement end) {
    if(this == end) return;

    final CompositeElement parent = getTreeParent();
    final TreeElement startPrev = getTreePrev();
    final TreeElement endPrev = end != null ? end.getTreePrev() : null;

    assert end == null || end.getTreeParent() == parent : "Trying to remove non-child";

    if (parent != null){
      if (this == parent.getFirstChildNode()) {
        parent.setFirstChildNode(end);
      }
      if (end == null) {
        parent.setLastChildNode(startPrev);
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

    if (parent != null){
      for(TreeElement element = this; element != null; element = element.getTreeNext()){
        element.setTreeParent(null);
        element.onInvalidated();
      }
    }

    if (DebugUtil.CHECK){
      if (parent != null){
        DebugUtil.checkTreeStructure(parent);
      }
      DebugUtil.checkTreeStructure(this);
    }
  }

  public IElementType getElementType() {
    return myType;
  }
}

