// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static com.intellij.reference.SoftReference.dereference;

/**
 * This class is responsible for storing range marker trees on virtual files without keeping documents alive.
 * <p>
 * Virtual files keep weak references to range marker trees, so retained markers can be transferred to a recreated document without
 * keeping the old document alive. The class also hosts helpers for creating range markers before the document is loaded.
 */
@ApiStatus.Internal
public final class RMTreeReference extends WeakReference<RangeMarkerTree<RangeMarkerEx>> {
  private final @NotNull VirtualFile virtualFile;

  RMTreeReference(@NotNull RangeMarkerTree<RangeMarkerEx> referent, @NotNull VirtualFile virtualFile) {
    super(referent, RM_TREE_QUEUE);
    this.virtualFile = virtualFile;
  }

  // track GC of RangeMarkerTree: means no one is interested in range markers for this file anymore
  private static final ReferenceQueue<RangeMarkerTree<RangeMarkerEx>> RM_TREE_QUEUE = new ReferenceQueue<>();

  private static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> RANGE_MARKERS_KEY = Key.create("RANGE_MARKERS_KEY");
  private static final Key<Reference<RangeMarkerTree<RangeMarkerEx>>> PERSISTENT_RANGE_MARKERS_KEY = Key.create("PERSISTENT_RANGE_MARKERS_KEY");

  /**
   * makes range marker without creating the document (which could be expensive)
   */
  public static @NotNull RangeMarker createRangeMarkerForVirtualFile(
    @NotNull VirtualFile file,
    int offset,
    int startLine,
    int startCol,
    int endLine,
    int endCol,
    boolean persistent
  ) {
    int estimatedLength = RangeMarkerImpl.estimateDocumentLength(file);
    offset = Math.min(offset, estimatedLength);
    RangeMarkerImpl marker = persistent
                             ? new PersistentRangeMarker(file, offset, offset, startLine, startCol, endLine, endCol, estimatedLength, false)
                             : new RangeMarkerImpl(file, offset, offset, estimatedLength, false);
    Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key = persistent ? PERSISTENT_RANGE_MARKERS_KEY : RANGE_MARKERS_KEY;
    RangeMarkerTree<RangeMarkerEx> tree;
    while (true) {
      Reference<RangeMarkerTree<RangeMarkerEx>> oldRef = file.getUserData(key);
      tree = dereference(oldRef);
      if (tree != null) break;
      tree = new RangeMarkerTree<>();
      RMTreeReference reference = new RMTreeReference(tree, file);
      if (file.replace(key, oldRef, reference)) break;
    }
    tree.addInterval(marker, offset, offset, false, false, false, 0);
    return marker;
  }

  public static void processQueue() {
    RMTreeReference ref;
    while ((ref = (RMTreeReference)RM_TREE_QUEUE.poll()) != null) {
      ref.virtualFile.replace(RANGE_MARKERS_KEY, ref, null);
      ref.virtualFile.replace(PERSISTENT_RANGE_MARKERS_KEY, ref, null);
    }
  }

  // are some range markers retained by strong references?
  public static boolean areRangeMarkersRetainedFor(@NotNull VirtualFile f) {
    processQueue();
    // if a marker is retained, then so is its node and the whole tree
    // (ignore the race when marker is gc-ed right after this call - it's harmless)
    return dereference(f.getUserData(RANGE_MARKERS_KEY)) != null ||
           dereference(f.getUserData(PERSISTENT_RANGE_MARKERS_KEY)) != null;
  }

  @TestOnly
  public static boolean areRangeMarkersRetainedFor0(@NotNull VirtualFile f) {
    return f.getUserData(RANGE_MARKERS_KEY) != null ||
           f.getUserData(PERSISTENT_RANGE_MARKERS_KEY) != null;
  }

  static void getSaveRMTree(
    @NotNull VirtualFile f,
    @NotNull DocumentEx d,
    @NotNull RangeMarkerTree<RangeMarkerEx> rmt,
    @NotNull RangeMarkerTree<RangeMarkerEx> prmt,
    int tabSize
  ) {
    processQueue();
    getSaveRMTree(f, d, RANGE_MARKERS_KEY, rmt, tabSize);
    getSaveRMTree(f, d, PERSISTENT_RANGE_MARKERS_KEY, prmt, tabSize);
  }

  private static void getSaveRMTree(
    @NotNull VirtualFile f,
    @NotNull DocumentEx d,
    @NotNull Key<Reference<RangeMarkerTree<RangeMarkerEx>>> key,
    @NotNull RangeMarkerTree<RangeMarkerEx> tree,
    int tabSize
  ) {
    RMTreeReference freshRef = new RMTreeReference(tree, f);
    Reference<RangeMarkerTree<RangeMarkerEx>> oldRef;
    do {
      oldRef = f.getUserData(key);
    }
    while (!f.replace(key, oldRef, freshRef));
    RangeMarkerTree<RangeMarkerEx> oldTree = dereference(oldRef);

    if (oldTree == null) {
      // no tree was saved in virtual file before (happens when a new document is created).
      // or the old tree got gc-ed, because no reachable markers retaining it are left alive. good riddance.
      return;
    }

    // Some old tree was saved in the virtual file. Have to transfer markers from there.
    oldTree.copyRangeMarkersTo(d, tabSize);
  }
}
