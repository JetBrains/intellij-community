// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier

/**
 * A secondary "comment" label: smaller, dimmed text that renders HTML and opens `<a href>` links in a
 * browser, matching the Kotlin UI DSL `comment(...)`.
 *
 * [maxLineLength] is the width in characters the text wraps at. [MAX_LINE_LENGTH_WORD_WRAP] wraps it to
 * whatever width the comment is given instead, which is what a comment standing on a row of its own does;
 * [com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH] is what a comment under a single component does.
 *
 * @see com.intellij.ui.dsl.builder.components.DslLabel
 * @see com.intellij.ui.dsl.builder.Row.comment
 */
@Composable
@ApiStatus.Experimental
public fun Comment(
  text: @NlsContexts.DetailedDescription String,
  maxLineLength: Int = MAX_LINE_LENGTH_WORD_WRAP,
  modifier: SwingModifier = SwingModifier,
) {
  SwingNode(
    factory = {
      DslLabel(DslLabelType.COMMENT).apply {
        action = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE
      }
    },
    update = {
      // A comment that wraps to the width it is given has no width of its own to ask for, so it is held to
      // the one it gets; the width is set before the text so the text is laid out to it straight away.
      set(maxLineLength) {
        this.maxLineLength = it
        limitPreferredSize = it == MAX_LINE_LENGTH_WORD_WRAP
      }
      set(text) { this.text = it }
      applyModifier(modifier)
    },
  )
}
