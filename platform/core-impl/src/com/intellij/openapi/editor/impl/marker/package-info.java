// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Provides range markers for immutable document snapshots.
 *
 * <p>Each {@link com.intellij.openapi.editor.impl.marker.PMarkerRoot} is an immutable value.
 * A {@link com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore} keeps mutable root references outside snapshots.</p>
 *
 * <p>{@link com.intellij.openapi.editor.impl.marker.SnapshotMarkerStores} applies each document operation to every active store.
 * {@link com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl} creates markers and resolves their state in selected roots.</p>
 *
 * <p><img src="marker-classes.svg" width="1000" height="589" alt="Factories, stores, and marker types for snapshot markers"></p>
 */
@Internal
package com.intellij.openapi.editor.impl.marker;

import org.jetbrains.annotations.ApiStatus.Internal;
