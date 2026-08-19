// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

/**
 * Immutable marker configuration.
 */
data class MarkerSpec(
    /**
     * Controls insertion at the marker start offset.
     *
     * When `true`, inserted text becomes part of the marker range. The start
     * remains before the inserted text.
     *
     * When `false`, the start moves after the inserted text.
     */
    val isGreedyToLeft: Boolean,
    /**
     * Controls insertion at the marker end offset.
     *
     * When `true`, inserted text becomes part of the marker range. The end
     * moves after the inserted text.
     *
     * When `false`, the end remains before the inserted text.
     */
    val isGreedyToRight: Boolean,
    /**
     * Controls which side of inserted text a zero-length marker remains on.
     *
     * When `true`, a non-greedy marker moves after text inserted at its offset.
     *
     * When `false`, it remains before the inserted text.
     */
    val isStickingToRight: Boolean = false,
)
