// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Provides range markers for immutable document snapshots.
 *
 * <p>Each {@link com.intellij.openapi.editor.impl.marker.PMarkerRoot} is an immutable value.
 * A {@link com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore} keeps mutable root references outside snapshots.</p>
 *
 * <p>{@link com.intellij.openapi.editor.impl.marker.SnapshotMarkerStores} applies each document operation to every active store.
 * {@link com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngine} creates marker handles and resolves them against selected roots.</p>
 */
@Internal
package com.intellij.openapi.editor.impl.marker;

import org.jetbrains.annotations.ApiStatus.Internal;
