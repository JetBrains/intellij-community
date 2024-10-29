// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.ReparseableASTNode;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.ReadOnlyLightVirtualFile;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeElement extends ElementBase implements ASTNode, ReparseableASTNode, Cloneable, LighterASTNode {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];
  private TreeElement myNextSibling;
  private TreeElement myPrevSibling;
  private CompositeElement myParent;

  private final IElementType myType;
  private volatile int myStartOffsetInParent = -1;

  public TreeElement(@NotNull IElementType type) {
    myType = type;
  }

  private static PsiFileImpl getCachedFile(@NotNull TreeElement each) {
    FileElement node = (FileElement)SharedImplUtil.findFileElement(each);
    return node == null ? null : (PsiFileImpl)node.getCachedPsi();
  }

  @Override
  public @NotNull Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.myNextSibling = null;
    clone.myPrevSibling = null;
    clone.myParent = null;
    clone.myStartOffsetInParent = -1;
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
      return PsiManagerEx.getInstanceEx(project);
    }
    TreeElement element;
    CompositeElement parent;
    for (element = this; (parent = element.getTreeParent()) != null; element = parent) {
    }
    if (element instanceof FileElement) { //TODO!!
      return element.getManager();
    }
    parent = getTreeParent();
    if (parent != null) {
      return parent.getManager();
    }
    return null;
  }

  @Override
  public abstract LeafElement findLeafElementAt(int offset);

  public abstract char @NotNull [] textToCharArray();

  @Override
  public abstract TreeElement getFirstChildNode();

  @Override
  public abstract TreeElement getLastChildNode();

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

  @Override
  public final int getStartOffsetInParent() {
    if (myParent == null) return -1;
    int offsetInParent = myStartOffsetInParent;
    if (offsetInParent != -1) return offsetInParent;

    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      assertReadAccessAllowed();
    }

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

  @Override
  public abstract int getTextLength();

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public int getEndOffset() {
    return getStartOffset() + getTextLength();
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

  @Override
  public @NonNls String toString() {
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
    if (parent == myParent) return;

    if (myParent != null) {
      PsiFileImpl file = getCachedFile(this);
      if (file != null) {
        file.beforeAstChange();
      }
    }

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

  static void clearRelativeOffsets(TreeElement element) {
    TreeElement cur = element;
    while (cur != null && cur.myStartOffsetInParent != -1) {
      cur.myStartOffsetInParent = -1;
      cur = cur.getTreeNext();
    }
  }

  public void clearCaches() {
  }

  @Override
  public final boolean equals(Object obj) {
    return obj == this;
  }

  public abstract int hc(); // Used in tree diffing

  public abstract void acceptTree(@NotNull TreeElementVisitor visitor);

  protected void onInvalidated() {
    DebugUtil.onInvalidated(this);
  }

  @Override
  public final void applyReplaceOnReparse(@NotNull ASTNode newChild) {
    TreeElement newTreeElement = (TreeElement)newChild;
    newTreeElement.rawRemove();
    rawReplaceWithList(newTreeElement);

    newTreeElement.clearCaches();
    if (!(newTreeElement instanceof FileElement)) {
      newTreeElement.getTreeParent().subtreeChanged();
    }
  }

  public void rawInsertBeforeMe(@NotNull TreeElement firstNew) {
    TreeElement anchorPrev = getTreePrev();
    if(anchorPrev == null){
      firstNew.rawRemoveUpToLast();
      CompositeElement p = getTreeParent();
      if(p != null) p.setFirstChildNode(firstNew);
      while(true){
        TreeElement treeNext = firstNew.getTreeNext();
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

    CompositeElement parent = getTreeParent();
    if (parent != null) {
      parent.subtreeChanged();
    }
  }

  final void rawInsertAfterMeWithoutNotifications(@NotNull TreeElement firstNew) {
    firstNew.rawRemoveUpToWithoutNotifications(null, false);
    CompositeElement p = getTreeParent();
    TreeElement treeNext = getTreeNext();
    firstNew.setTreePrev(this);
    setTreeNext(firstNew);
    while(true){
      TreeElement n = firstNew.getTreeNext();
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
    TreeElement next = getTreeNext();
    CompositeElement parent = getTreeParent();
    TreeElement prev = getTreePrev();

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

  public void rawReplaceWithList(@Nullable TreeElement firstNew) {
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
  final void rawRemoveUpToWithoutNotifications(@Nullable TreeElement end, boolean invalidate) {
    if(this == end) return;

    CompositeElement parent = getTreeParent();
    TreeElement startPrev = getTreePrev();
    TreeElement endPrev = end != null ? end.getTreePrev() : null;

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
  public @NotNull IElementType getElementType() {
    return myType;
  }

  void assertReadAccessAllowed() {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) return;
    FileElement fileElement = TreeUtil.getFileElement(this);
    PsiElement psi = fileElement == null ? null : fileElement.getCachedPsi();
    if (psi == null) return;
    FileViewProvider provider = psi instanceof PsiFile ? ((PsiFile)psi).getViewProvider() : null;
    boolean ok = provider != null && provider.getVirtualFile() instanceof ReadOnlyLightVirtualFile;
    if (!ok) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
  }
}

