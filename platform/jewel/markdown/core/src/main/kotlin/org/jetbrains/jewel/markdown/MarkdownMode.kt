// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

@ExperimentalJewelApi
public sealed interface MarkdownMode {
    public val withEditor: Boolean
    public val scrollingSynchronizer: ScrollingSynchronizer?

    public object PreviewOnly : MarkdownMode {
        override val withEditor: Boolean = false
        override val scrollingSynchronizer: ScrollingSynchronizer? = null
    }

    public class WithEditor(public override val scrollingSynchronizer: ScrollingSynchronizer?) : MarkdownMode {
        override val withEditor: Boolean = true
    }
}

@ExperimentalJewelApi
@Composable
public fun WithMarkdownMode(mode: MarkdownMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMarkdownMode provides mode) { content() }
}
