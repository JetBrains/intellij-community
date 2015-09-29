/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeElement extends ElementBase implements ASTNode, Cloneable {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  private TreeElement myNextSibling;
  private TreeElement myPrevSibling;
  private CompositeElement myParent;

  private final IElementType myType;
  private volatile int myStartOffsetInParent = -1;

  public TreeElement(@NotNull IElementType type) {
    myType = type;
  }

  @NotNull
  @Override
  public Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    synchronized (PsiLock.LOCK) {
      clone.myNextSibling = null;
      clone.myPrevSibling = null;
      clone.myParent = null;
      clone.myStartOffsetInParent = -1;
    }

    return clone;
  }

  @Override
  public ASTNode copyElement() {
    CharTable table = SharedImplUtil.findCharTableByTree(this);
    return ChangeUtil.copyElement(this, table);
  }

  public PsiManagerEx getManager() {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return (PsiManagerEx)PsiManager.getInstance(project);
    }
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

  @Override
  public abstract LeafElement findLeafElementAt(int offset);

  @NotNull
  public abstract char[] textToCharArray();

  @Override
  public abstract TreeElement getFirstChildNode();

  @Override
  public abstract TreeElement getLastChildNode();

  public abstract int getNotCachedLength();

  public abstract int getCachedLength();

  @Override
  public TextRange getTextRange() {
    int start = getStartOffset();
    return new TextRange(start, start + getTextLength());
  }

  @Override
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

    ApplicationManager.getApplication().assertReadAccessAllowed();

    TreeElement cur = this;
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

  public int getTextOffset() {
    return getStartOffset();
  }

  public boolean textMatches(@NotNull CharSequence buffer, int startOffset, int endOffset) {
    return textMatches(buffer, startOffset) == endOffset;
  }

  protected abstract int textMatches(@NotNull CharSequence buffer, int start);

  public boolean textMatches(@NotNull CharSequence seq) {
    return textMatches(seq, 0, seq.length());
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getTextLength() == element.getTextLength() && textMatches(element.getText());
  }

  @NonNls
  public String toString() {
    return "Element" + "(" + getElementType() + ")";
  }

  @Override
  public final CompositeElement getTreeParent() {
    return myParent;
  }

  @Override
  public final TreeElement getTreePrev() {
    return myPrevSibling;
  }

  final void setTreeParent(CompositeElement parent) {
    myParent = parent;
    if (parent != null && parent.getElementType() != TokenType.DUMMY_HOLDER) {
      DebugUtil.revalidateNode(this);
    }
  }

  final void setTreePrev(TreeElement prev) {
    myPrevSibling = prev;
    clearRelativeOffsets(this);
  }

  @Override
  public final TreeElement getTreeNext() {
    return myNextSibling;
  }

  final void setTreeNext(TreeElement next) {
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

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  public final boolean equals(Object obj) {
    return obj == this;
  }

  public abstract int hc(); // Used in tree diffing

  public abstract void acceptTree(TreeElementVisitor visitor);

  protected void onInvalidated() {
    DebugUtil.onInvalidated(this);
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
      if (p != null) {
        p.subtreeChanged();
      }
    }
    else anchorPrev.rawInsertAfterMe(firstNew);

    DebugUtil.checkTreeStructure(this);
  }

  public void rawInsertAfterMe(@NotNull TreeElement firstNew) {
    rawInsertAfterMeWithoutNotifications(firstNew);

    final CompositeElement parent = getTreeParent();
    if (parent != null) {
      parent.subtreeChanged();
    }
  }

  protected final void rawInsertAfterMeWithoutNotifications(TreeElement firstNew) {
    firstNew.rawRemoveUpToWithoutNotifications(null, false);
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
    DebugUtil.checkTreeStructure(this);
  }

  public void rawRemove() {
    final TreeElement next = getTreeNext();
    final CompositeElement parent = getTreeParent();
    final TreeElement prev = getTreePrev();

    if(prev != null){
      prev.setTreeNext(next);
    }
    else if(parent != null) {
      parent.setFirstChildNode(next);
    }

    if(next != null){
      next.setTreePrev(prev);
    }
    else if(parent != null) {
      parent.setLastChildNode(prev);
    }

    DebugUtil.checkTreeStructure(parent);
    DebugUtil.checkTreeStructure(prev);
    DebugUtil.checkTreeStructure(next);

    invalidate();
  }

  public void rawReplaceWithList(TreeElement firstNew) {
    if (firstNew != null){
      rawInsertAfterMeWithoutNotifications(firstNew);
    }
    rawRemove();
  }

  protected void invalidate() {
    CompositeElement parent = getTreeParent();
    if (parent != null) {
      parent.subtreeChanged();
    }

    onInvalidated();
    setTreeNext(null);
    setTreePrev(null);
    setTreeParent(null);
  }

  public void rawRemoveUpToLast() {
    rawRemoveUpTo(null);
  }

  // remove nodes from this[including] to end[excluding] from the parent
  public void rawRemoveUpTo(@Nullable TreeElement end) {
    CompositeElement parent = getTreeParent();

    rawRemoveUpToWithoutNotifications(end, true);

    if (parent != null) {
      parent.subtreeChanged();
    }
  }

  // remove nodes from this[including] to end[excluding] from the parent
  protected final void rawRemoveUpToWithoutNotifications(TreeElement end, boolean invalidate) {
    if(this == end) return;

    final CompositeElement parent = getTreeParent();
    final TreeElement startPrev = getTreePrev();
    final TreeElement endPrev = end != null ? end.getTreePrev() : null;

    assert end == null || end.getTreeParent() == parent : "Trying to remove non-child";

    if (end != null) {
      TreeElement element;
      for (element = this; element != end && element != null; element = element.getTreeNext());
      assert element == end : end + " is not successor of " + this +" in the .getTreeNext() chain";
    }
    if (parent != null){
      if (getTreePrev() == null) {
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
        if (invalidate) {
          element.onInvalidated();
        }
      }
    }

    DebugUtil.checkTreeStructure(parent);
    DebugUtil.checkTreeStructure(this);
  }

  @Override
  @NotNull
  public IElementType getElementType() {
    return myType;
  }
}

