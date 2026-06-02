// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.EditorLockFreeTyping;
import com.intellij.openapi.application.ThreadingRuntimeFlagsKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.Key;
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
import com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap;
import com.intellij.psi.impl.source.tree.mvcc.VersionedPsiConsistencyException;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersionRegistry;
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


  // We need to perform atomic operations on fields of this class.
  // AtomicReference takes more space than a plain reference (since it is a wrapper over `VarHandle`), so we use `VarHandle`s directly.
  private static final VarHandleWrapper myNextSiblingAccessor = VarHandleWrapper.getFactory().create(TreeElement.class, "myNextSibling", Object.class);
  private static final VarHandleWrapper myPrevSiblingAccessor = VarHandleWrapper.getFactory().create(TreeElement.class, "myPrevSibling", Object.class);
  private static final VarHandleWrapper myParentAccessor = VarHandleWrapper.getFactory().create(TreeElement.class, "myParent", Object.class);
  private static final VarHandleWrapper myStartOffsetInParentAccessor = VarHandleWrapper.getFactory().create(TreeElement.class, "myStartOffsetInParent", Object.class);

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
  // not final because of `clone` and possibility of transitioning to versioned state during insertion
  private volatile long creationVersion = InternalPsiVersioning.getCreationPsiVersionForElement();

  /*
    The following fields represent edges in the syntax tree graph.
    They are either a direct reference to an object (like `TreeElement`), or a reference to `VersionedPayloadMap`.

    In the first case, these fields represent a direct reference associated with `creationVersion`.
    If a field is a map, then it acts as a sorted map from long to `TreeElement`. In this case, the payloads in this map are ordered by versions.
   */

   /**
   * A versioned reference to {@link CompositeElement}.
   * @see doSetMyParent
   * @see doGetMyParent
   */
  private volatile @Nullable Object myParent = null;

  /**
   * A versioned reference to {@link TreeElement}.
   * @see doSetMyNextSibling
   * @see doGetMyNextSibling
   */
  private volatile @Nullable Object myNextSibling = null;

  /**
   * A versioned reference to {@link TreeElement}.
   * @see doSetMyPrevSibling
   * @see doGetMyPrevSibling
   */
  private volatile @Nullable Object myPrevSibling = null;

  private final IElementType myType;

  /**
   * A versioned reference to {@link Integer}.
   * @see doGetMyStartOffsetInParent
   * @see doSetMyStartOffsetInParent
   */
  private volatile @Nullable Object myStartOffsetInParent = null;

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
  protected final void setVersionedField(@NotNull VarHandleWrapper wrapper, long version, @Nullable Object payload) {
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
        if (newMap != null) {
          // we make an attempt to compact a map.
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
   * <p>
   * This cleanup is relevant only for maps -- only then we know that there are multiple versions, of which some can be cleaned up.
   * We do not do the traditional compare-and-swap loop here -- if an attempt of garbage collection fails,
   * then it means that someone else made progress, and they themselves will initiate another round of garbage collection.
   * This makes the cleanup procedure wait-free.
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
  protected final @Nullable Object getVersionedField(Object currentlyStoredValue, long version) {
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

  private void doSetMyNextSibling(long version, TreeElement nextSibling) {
    if (version == -1) {
      this.myNextSibling = nextSibling;
    } else {
      setVersionedField(myNextSiblingAccessor, version, nextSibling);
    }
  }

  private TreeElement doGetMyNextSibling(long version) {
    if (version == -1) {
      return (TreeElement)this.myNextSibling;
    } else {
      Object result = getVersionedField(this.myNextSibling,  version);
      return (TreeElement)result;
    }
  }

  private void doSetMyPrevSibling(long version, TreeElement prevSibling) {
    if (version == -1) {
      this.myPrevSibling = prevSibling;
    } else {
      setVersionedField(myPrevSiblingAccessor, version, prevSibling);
    }
  }

  private TreeElement doGetMyPrevSibling(long version) {
    if (version == -1) {
      return (TreeElement)this.myPrevSibling;
    } else {
      Object result = getVersionedField(this.myPrevSibling, version);
      return (TreeElement)result;
    }
  }

  private void doSetMyParent(long version, CompositeElement parent) {
    if (version == -1) {
      this.myParent = parent;
    } else {
      setVersionedField(myParentAccessor, version, parent);
    }
  }

  private CompositeElement doGetMyParent(long version) {
    if (version == -1) {
      return (CompositeElement)this.myParent;
    } else {
      Object result = getVersionedField(this.myParent, version);
      return (CompositeElement)result;
    }
  }

  private void doSetMyStartOffsetInParent(long version, int startOffsetInParent) {
    Integer cacheable = startOffsetInParent == -1 ? null : Integer.valueOf(startOffsetInParent);
    if (version == -1) {
      this.myStartOffsetInParent = cacheable;
    } else {
      setVersionedField(myStartOffsetInParentAccessor, version, cacheable);
    }
  }

  private int doGetMyStartOffsetInParent(long version) {
    Integer startOffset;
    if (version == -1) {
      startOffset = (Integer)this.myStartOffsetInParent;
    } else {
      startOffset = (Integer)getVersionedField(this.myStartOffsetInParent, version);
    }
    return startOffset == null ? -1 : startOffset.intValue();
  }

  @Override
  public @NotNull Object clone() {
    TreeElement clone = (TreeElement)super.clone();
    clone.creationVersion = -1L;
    myNextSiblingAccessor.setVolatile(clone, null);
    myPrevSiblingAccessor.setVolatile(clone, null);
    myParentAccessor.setVolatile(clone, null);
    myStartOffsetInParentAccessor.setVolatile(clone, null);
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
    long version = getVersionForReading();
    TreeElement element;
    CompositeElement parent;
    for (element = this; (parent = element.getTreeParentVersioned(version)) != null; element = parent) {
    }
    if (element instanceof FileElement) { //TODO!!
      return element.getManager();
    }
    parent = getTreeParentVersioned(version);
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

  /**
   * An optimized version of {@link #getCachedLength()} that allows skipping querying the thread-local version.
   */
  @ApiStatus.Internal
  protected int getCachedLengthVersioned(long version) {
    return getCachedLength();
  }

  /**
   * An optimized version of {@link #getFirstChildNode()} that allows skipping querying the thread-local version.
   */
  @ApiStatus.Internal
  public TreeElement getFirstChildNodeVersioned(long version) {
    return getFirstChildNode();
  }

  /**
   * An optimized version of {@link #getLastChildNode()} that allows skipping querying the thread-local version.
   */
  @ApiStatus.Internal
  protected TreeElement getLastChildNodeVersioned(long version) {
    return getLastChildNode();
  }

  @Override
  public TextRange getTextRange() {
    long version = getVersionForReading();
    return getTextRangeVersioned(version);
  }

  private TextRange getTextRangeVersioned(long version) {
    int start = getStartOffsetVersioned(version);
    return new TextRange(start, start + getTextLengthVersioned(version));
  }

  @Override
  public int getStartOffset() {
    long version = getVersionForReading();
    return getStartOffsetVersioned(version);
  }

  @ApiStatus.Internal
  int getStartOffsetVersioned(long version) {
    int result = 0;
    TreeElement current = this;
    CompositeElement parent = current.doGetMyParent(version);
    while (parent != null) {
      result += current.getStartOffsetInParentVersioned(version);
      current = parent;
      parent = current.doGetMyParent(version);
    }

    return result;
  }

  @Override
  public final int getStartOffsetInParent() {
    long version = getVersionForReading();
    return getStartOffsetInParentVersioned(version);
  }

  private int getStartOffsetInParentVersioned(long version) {
    if (doGetMyParent(version) == null) return -1;
    int offsetInParent = doGetMyStartOffsetInParent(version);
    if (offsetInParent != -1) return offsetInParent;

    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      assertReadAccessAllowed();
    }

    TreeElement cur = this;
    while (true) {
      TreeElement prev = cur.getTreePrevVersioned(version);
      if (prev == null) break;
      cur = prev;
      offsetInParent = cur.doGetMyStartOffsetInParent(version);
      if (offsetInParent != -1) break;
    }

    if (offsetInParent == -1) {
      offsetInParent = 0;
      cur.doSetMyStartOffsetInParent(version, offsetInParent);
    }

    while (cur != this) {
      TreeElement next = cur.getTreeNextVersioned(version);
      offsetInParent += cur.getTextLengthVersioned(version);
      next.doSetMyStartOffsetInParent(version, offsetInParent);
      cur = next;
    }
    return offsetInParent;
  }

  @Override
  public abstract int getTextLength();


  @ApiStatus.Internal
  public int getTextLengthVersioned(long version) {
    return getTextLength();
  }

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
    long version = getVersionForReading();
    return doGetMyParent(version);
  }

  @ApiStatus.Internal
  public final CompositeElement getTreeParentVersioned(long version) {
    return doGetMyParent(version);
  }

  @Override
  public final TreeElement getTreePrev() {
    long version = getVersionForReading();
    return getTreePrevVersioned(version);
  }
  /**
   * An optimized version of {@link #getTreePrev()} that allows skipping querying the thread-local version.
   */
  @ApiStatus.Internal
  protected final TreeElement getTreePrevVersioned(long version) {
    return doGetMyPrevSibling(version);
  }

  final void setTreeParent(long version, CompositeElement parent) {
    assertElementCompatibility(this, parent);
    CompositeElement myActualParent = doGetMyParent(version);
    if (parent == myActualParent) return;

    if (myActualParent != null) {
      PsiFileImpl file = getCachedFile(this);
      if (file != null) {
        file.beforeAstChange();
      }
    }

    doSetMyParent(version, parent);
    if (parent != null && parent.getElementType() != TokenType.DUMMY_HOLDER) {
      DebugUtil.revalidateNode(this);
    }
  }

  final void setTreeParent(CompositeElement parent) {
    long writeVersion = getVersionForWriting();
    setTreeParent(writeVersion, parent);
  }

  /**
   * In the IntelliJ Platform, it is normal to create a non-versioned PSI element and insert it into versioned tree
   * When this happens, we detach an element from its non-versioned structure, modify its internal structure so it is versioned,
   * and then we insert it into the versioned structure.
   * <p>
   * This helps us to maintain the versioning consistency of syntax trees while retaining old contracts.
   */
  @ApiStatus.Internal
  protected final void ensureVersioned(long version, @Nullable TreeElement element) {
    if (!this.isVersioned() || element == null || element.creationVersion != -1) {
      return;
    }
    element.creationVersion = version;
    // we do not traverse siblings -- they can remain non-versioned;
    // but we need to make the entire subtree versioned
    element.doEnsureVersioned(version);
  }

  @ApiStatus.Internal
  protected void doEnsureVersioned(long version) {
    // here we add traversal of children in overloads
  }

  /**
   * We permit only interactions between elements that are either simultaneously versioned or simultaneously non-versioned.
   */
  @ApiStatus.Internal
  protected static void assertElementCompatibility(@NotNull TreeElement primary, @Nullable TreeElement secondary) {
    if (!ThreadingRuntimeFlagsKt.getAssertTreeElementVersioningCompatibility()) {
      return;
    }
    if (secondary == null) {
      return;
    }
    boolean firstIsVersioned = primary.isVersioned();
    boolean secondIsVersioned = secondary.isVersioned();
    if (firstIsVersioned != secondIsVersioned) {
      throw getVersionInconsistencyException(primary, secondary);
    }
  }

  private static @NotNull RuntimeException getVersionInconsistencyException(@NotNull TreeElement primary, @NotNull TreeElement secondary) {
    return new VersionedPsiConsistencyException.TreeElement(
        "Tree elements " + primary + " (versioned: " + primary.isVersioned() + ") and " + secondary + " (versioned: "+ secondary.isVersioned() + ") are not compatible from versioning point of view.\n" +
        "Most likely you created or copied some PSI element and then tried to attach it to a physical PSI tree.\n" +
        "The solution is to create or copy elements in the same environment (i.e., inside / outside of write actions),\n" +
        "or to use `com.intellij.psi.util.PsiVersioningService.createVersionedPsiElements` before creating or copying an element.");
  }

  @ApiStatus.Internal
  public final boolean isVersioned() {
    return creationVersion != -1L;
  }

  @ApiStatus.Internal
  protected final long getVersionForWriting() {
    if (isVersioned()) {
      InternalPsiVersioning.assertWritePsiModificationAllowed();
      return InternalPsiVersioning.getCurrentPsiVersion();
    } else {
      return -1; // will be ignored
    }
  }

  @ApiStatus.Internal
  protected final long getVersionForReading() {
    if (isVersioned()) {
      return InternalPsiVersioning.getCurrentPsiVersion();
    } else {
      return -1; // will be ignored
    }
  }

  final void setTreePrev(TreeElement prev) {
    long version = getVersionForWriting();
    setTreePrev(version, prev);
  }

  final void setTreePrev(long version, TreeElement prev) {
    assertElementCompatibility(this, prev);
    doSetMyPrevSibling(version, prev);
    clearRelativeOffsets(version, this);
  }

  @Override
  public final TreeElement getTreeNext() {
    long version = getVersionForReading();
    return doGetMyNextSibling(version);
  }

  @ApiStatus.Internal
  public final TreeElement getTreeNextVersioned(long version) {
    return doGetMyNextSibling(version);
  }

  final void setTreeNext(TreeElement next) {
    long version = getVersionForWriting();
    setTreeNext(version, next);
  }

  final void setTreeNext(long version, TreeElement next) {
    assertElementCompatibility(this, next);
    doSetMyNextSibling(version, next);
    clearRelativeOffsets(version, next);
  }

  static void clearRelativeOffsets(long version, TreeElement element) {
    if (element == null) {
      return;
    }
    TreeElement cur = element;
    while (cur != null) {
      // Before the introduction of persistent PSI changes, we had a special exit condition in this loop:
      // if offsetInParent was already removed, we did not we proceed with dropping this cache.
      // With persistent PSI, this heuristic no longer works: the cache can be erased in two versions, and initialization in the earlier version
      // must not influence the later version.
      //
      // So here we are trying to retain this logic, but we need to be careful: this iteration can be a very hot path,
      // as shown in the test `XmlPerformanceTest.testPerformance5` -- we can accidentally start running O(n^2) offset cleanups for wide enough trees.
      //
      // So here we go unusually low-level and adapt the heuristic described above. Mainly, we are checking for explicitly erased values for a particular version.
      if (cur.creationVersion == -1 && cur.myStartOffsetInParent == null) {
        // a non-versioned tree element already has erased offsetInParent; we can safely abort the loop
        break;
      } else if (cur.creationVersion != -1) {
        Object offset = myStartOffsetInParentAccessor.getVolatile(cur);
        if (version == cur.creationVersion && offset == null) {
          // a versioned tree element is not expanded, has erased offsetInParent, and we are trying to erase it for its creation version.
          // thus it is safe to abort here also.
          break;
        }
        if (offset instanceof VersionedPayloadMap && ((VersionedPayloadMap)offset).explicitlyRemoved(version)) {
          // a versioned tree element is expanded, and the version was explicitly removed earlier.
          // so we can also abort here, as all elements further are also removed.
          break;
        }
      }
      cur.doSetMyStartOffsetInParent(version, -1);
      cur = cur.getTreeNextVersioned(version);
    }
  }

  public void clearCaches() {
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    // userdata retrieval is not yet supported in versioned mode
    InternalPsiVersioning.assertNotInFreezePsiVersion();
    return super.getUserData(key);
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
    long version = getVersionForWriting();
    if(anchorPrev == null){
      firstNew.rawRemoveUpToLast();
      ensureVersioned(version, firstNew);
      CompositeElement p = getTreeParentVersioned(version);
      if(p != null) p.setFirstChildNode(firstNew);
      while(true){
        TreeElement treeNext = firstNew.getTreeNextVersioned(version);
        ensureVersioned(version, treeNext);
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
    long version = getVersionForWriting();
    rawInsertAfterMeWithoutNotifications(version, firstNew);

    CompositeElement parent = getTreeParentVersioned(version);
    if (parent != null) {
      parent.subtreeChanged();
    }
  }

  final void rawInsertAfterMeWithoutNotifications(long version, @NotNull TreeElement firstNew) {
    if (!this.isVersioned() && firstNew.isVersioned()) {
      throw getVersionInconsistencyException(this, firstNew);
    }
    long versionForRemoval = firstNew.getVersionForWriting() == -1 ? -1 : version;
    firstNew.rawRemoveUpToWithoutNotifications(versionForRemoval, null, false);
    ensureVersioned(version, firstNew);
    CompositeElement p = getTreeParentVersioned(version);
    TreeElement treeNext = getTreeNextVersioned(version);
    firstNew.setTreePrev(version, this);
    setTreeNext(version, firstNew);
    while(true){
      TreeElement n = firstNew.getTreeNextVersioned(version);
      ensureVersioned(version, n);
      assert n != this : "Attempt to create cycle";
      firstNew.setTreeParent(version, p);
      if(n == null) break;
      firstNew = n;
    }

    if(treeNext == null){
      if(p != null){
        firstNew.setTreeParent(version, p);
        p.setLastChildNode(version, firstNew);
      }
    }
    else{
      firstNew.setTreeNext(version, treeNext);
      treeNext.setTreePrev(version, firstNew);
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
      rawInsertAfterMeWithoutNotifications(getVersionForWriting(), firstNew);
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
    long version = getVersionForWriting();
    CompositeElement parent = getTreeParentVersioned(version);

    rawRemoveUpToWithoutNotifications(version, end, true);

    if (parent != null) {
      parent.subtreeChanged();
    }
  }

  // remove nodes from this[including] to end[excluding] from the parent
  final void rawRemoveUpToWithoutNotifications(long version, @Nullable TreeElement end, boolean invalidate) {
    if (this == end) return;

    CompositeElement parent = getTreeParentVersioned(version);
    TreeElement startPrev = getTreePrevVersioned(version);
    TreeElement endPrev = end != null ? end.getTreePrevVersioned(version) : null;

    assert end == null || end.getTreeParentVersioned(version) == parent : "Trying to remove non-child";

    if (end != null) {
      TreeElement element = this;
      while (element != end && element != null) {
        element = element.getTreeNextVersioned(version);
      }
      assert element == end : end + " is not successor of " + this + " in the .getTreeNext() chain";
    }
    if (parent != null) {
      if (getTreePrev() == null) {
        parent.setFirstChildNode(version, end);
      }
      if (end == null) {
        parent.setLastChildNode(version, startPrev);
      }
    }
    if (startPrev != null) {
      startPrev.setTreeNext(version, end);
    }
    if (end != null) {
      end.setTreePrev(version, startPrev);
    }

    setTreePrev(version, null);
    if (endPrev != null) {
      endPrev.setTreeNext(version, null);
    }

    if (parent != null) {
      for (TreeElement element = this; element != null; element = element.getTreeNextVersioned(version)) {
        element.setTreeParent(version, null);
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
    if (InternalPsiVersioning.getCurrentPsiVersionInsideFrozenPsi() != null) {
      return;
    }
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

