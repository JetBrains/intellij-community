// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.EditorLockFreeTyping;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.AbstractFileViewProvider;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.ReparseableASTNode;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersionRegistry;
import com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ReadOnlyLightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.containers.VarHandleWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeElement extends ElementBase implements ASTNode, ReparseableASTNode, Cloneable, LighterASTNode {
  public static final TreeElement[] EMPTY_ARRAY = new TreeElement[0];

  /**
   * The threshold after which we try to clean up stale versions. There is no particular reason behind this number, it was chosen empirically.
   */
  private static final int GARBAGE_COLLECTION_LIMIT = 4;

  private TreeElement myNextSibling;
  private TreeElement myPrevSibling;
  private CompositeElement myParent;

  private final IElementType myType;
  private volatile int myStartOffsetInParent = -1;

  /**
   * The version (of versioned PSI feature) when this element is created.
   * <p>
   * We use this field to optimize memory usage of versioned graph edges.
   * In the most frequent scenario, the edge is set once at the creation of the class, and then it does not change.
   * It would be wasteful to allocate a new object for such cases, and it indeed leads to extremely high memory usage.
   * So instead we store the version at the level of a graph node, and it is implicitly associated with the values in edges.
   * If an edge is represented by a {@link VersionedPayloadMap}, then {@link creationVersion} has no effect.
   * <p>
   * If {@link creationVersion} is equal to `-1`, then this element is non-versioned.
   */
  // not final because of `clone`
  private volatile long creationVersion = InternalPsiVersioning.getCreationPsiVersionForElement();

  public TreeElement(@NotNull IElementType type) {
    myType = type;
  }

  private static PsiFileImpl getCachedFile(@NotNull TreeElement each) {
    FileElement node = (FileElement)SharedImplUtil.findFileElement(each);
    return node == null ? null : (PsiFileImpl)node.getCachedPsi();
  }

  /**
   * This function performs a lock-free modification of an edge in the graph.
   */
  @ApiStatus.Internal
  protected void setVersionedField(@NotNull VarHandleWrapper wrapper, long version, @Nullable Object payload) {
    while (true) { // loop because of compareAndSet
      Object currentlyStoredValue = wrapper.getVolatile(this);
      boolean isExpanded = currentlyStoredValue instanceof VersionedPayloadMap;
      if (version == this.creationVersion && !isExpanded) {
        if (currentlyStoredValue == payload) {
          return;
        }
        if (wrapper.compareAndSet(this, currentlyStoredValue, payload)) {
          return;
        }
      }
      else if (isExpanded) {
        VersionedPayloadMap versionedMap = (VersionedPayloadMap)currentlyStoredValue;
        VersionedPayloadMap newMap = versionedMap.insert(version, payload);
        if (newMap != null && !wrapper.compareAndSet(this, currentlyStoredValue, newMap)) {
          continue;
        }
        if (newMap != null && versionedMap.size() > GARBAGE_COLLECTION_LIMIT) {
          // if a map is too big, we make an attempt to compact it.
          runGarbageCollection(wrapper, newMap);
        }
        return;
      }
      else {
        // now we need to perform a transition from direct reference to a map of versions
        VersionedPayloadMap expanded = VersionedPayloadMap.create(creationVersion, currentlyStoredValue, version, payload);
        if (wrapper.compareAndSet(this, currentlyStoredValue, expanded)) {
          return;
        }
      }
    }
  }

  /**
   * A procedure to clean up stale versions.
   * First, cleanup is relevant only for maps -- only then we know that there are multiple versions, of which some can be cleaned up.
   * Second, since maps are based on atomic references, the cleanup itself is wait-free -- it makes an attempt to remove stale values.
   * Third, we do not do a traditional compare-and-swap loop here -- if an attempt of garbage collection fails,
   * then it means that someone else made progress, and they themselves will initiate another round of garbage collection.
   */
  private void runGarbageCollection(@NotNull VarHandleWrapper wrapper, @NotNull VersionedPayloadMap versionedMap) {
    PsiVersionRegistry service = PsiVersionRegistry.getInstance();
    long minVersion = Long.MAX_VALUE;
    for (long l : service.getFrozenKeys()) {
      minVersion = Long.min(minVersion, l);
    }
    long finalMinVersion = minVersion;
    VersionedPayloadMap newMap = versionedMap.cleanupStaleVersions(finalMinVersion);
    if (newMap != null) {
      wrapper.compareAndSet(this, versionedMap, newMap);
    }
  }

  /**
   * This function performs wait-free retrieval of an edge in the graph by version.
   * Technically, we could use `VarHandle` here, but it is more compiler-friendly to read the field directly.
   */
  @ApiStatus.Internal
  protected @Nullable Object getVersionedField(Object currentlyStoredValue, long version) {
    if (currentlyStoredValue instanceof VersionedPayloadMap) {
      VersionedPayloadMap map = (VersionedPayloadMap)currentlyStoredValue;
      return map.lowerBound(version);
    }
    else {
      // this is directly stored field, so we can return it right away.
      if (version >= creationVersion) {
        return currentlyStoredValue;
      }
      else {
        return null;
      }
    }
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
    if (this == end) return;

    CompositeElement parent = getTreeParent();
    TreeElement startPrev = getTreePrev();
    TreeElement endPrev = end != null ? end.getTreePrev() : null;

    assert end == null || end.getTreeParent() == parent : "Trying to remove non-child";

    if (end != null) {
      TreeElement element = this;
      while (element != end && element != null) {
        element = element.getTreeNext();
      }
      assert element == end : end + " is not successor of " + this + " in the .getTreeNext() chain";
    }
    if (parent != null) {
      if (getTreePrev() == null) {
        parent.setFirstChildNode(end);
      }
      if (end == null) {
        parent.setLastChildNode(startPrev);
      }
    }
    if (startPrev != null) {
      startPrev.setTreeNext(end);
    }
    if (end != null) {
      end.setTreePrev(startPrev);
    }

    setTreePrev(null);
    if (endPrev != null) {
      endPrev.setTreeNext(null);
    }

    if (parent != null) {
      for (TreeElement element = this; element != null; element = element.getTreeNext()) {
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
    if (provider == null) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      return;
    }
    VirtualFile virtualFile = provider.getVirtualFile();
    if (virtualFile instanceof ReadOnlyLightVirtualFile) {
      return;
    }
    if (isInElfScope(virtualFile)) return;
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  private static boolean isInElfScope(VirtualFile virtualFile) {
    if (EditorLockFreeTyping.isEnabled()) {
      if (EditorLockFreeTyping.isInElfScope(virtualFile)) {
        return true;
      }
      if (virtualFile instanceof LightVirtualFile) {
        VirtualFile originalFile = ((LightVirtualFile)virtualFile).getOriginalFile();
        if (EditorLockFreeTyping.isInElfScope(originalFile)) {
          return true;
        }
        if (virtualFile.getUserData(AbstractFileViewProvider.FREE_THREADED) == Boolean.TRUE) {
          // TODO: made in desperation, should be reworked
          return true;
        }
      }
    }
    return false;
  }
}
