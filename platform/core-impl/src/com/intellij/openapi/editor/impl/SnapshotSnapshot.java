// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentText;

/**
 * Pair of snapshots carried by a magic document core.
 * <p>
 * Clean state means both views share the same effective document state, so the
 * magic document can use the real snapshot in every context. Dirty state means
 * elf and real views have diverged, and the current context decides which snapshot
 * is visible until synchronization marks the pair clean again.
 * <p>
 * Intentionally as simple as possible.
 * <p>
 * For performance reasons, no if conditions, no methods, only raw final fields
 */
final class SnapshotSnapshot {
  /** Snapshot visible through the elf view while the pair is dirty. */
  final DocumentText elf;

  /** Snapshot visible through the authoritative real document view. */
  final DocumentText real;

  /**
   * Snapshot becomes dirty when
   * <ul>
   *   <li>any elf change occurred on the elf document side within elfScope</li>
   *   <li>any real change occurred on the real document side within elfScope</li>
   * </ul>
   */
  final boolean isDirty;

  private SnapshotSnapshot(DocumentText elf, DocumentText real, boolean isDirty) {
    this.elf = elf;
    this.real = real;
    this.isDirty = isDirty;
  }

  static SnapshotSnapshot newClean(DocumentText snapshot) {
    return new SnapshotSnapshot(snapshot, snapshot, false);
  }

  static SnapshotSnapshot newDirty(DocumentText elf, DocumentText real) {
    return new SnapshotSnapshot(elf, real, true);
  }
}
