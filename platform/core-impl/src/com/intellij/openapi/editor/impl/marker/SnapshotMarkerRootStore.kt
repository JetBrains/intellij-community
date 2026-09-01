// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import org.jetbrains.annotations.ApiStatus

/**
 * Stores marker roots outside a document snapshot and follows snapshot transitions.
 */
@ApiStatus.Internal
interface SnapshotMarkerRootStore {
  fun containsSnapshot(snapshot: DocumentSnapshot): Boolean

  fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch)

  fun inherit(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot)

  fun merge(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot, mergedSnapshot: DocumentSnapshot)
}
