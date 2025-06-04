// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** A [MarkdownBlock] that contains child blocks. */
@ExperimentalJewelApi
public interface WithChildBlocks {
    public val children: List<MarkdownBlock>
}
