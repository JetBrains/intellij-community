// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components.layout

import androidx.compose.runtime.Composable
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.annotations.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.components.layout.ScrollPaneScope
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import javax.swing.JScrollPane
import javax.swing.border.Border
import org.jetbrains.compose.swing.components.layout.ScrollPane as ComposeSwingScrollPane

/**
 * A [org.jetbrains.compose.swing.components.layout.ScrollPane] on the IDE's own scroll pane, a [JBScrollPane],
 * rather than a plain `JScrollPane`: the scroll bars, the viewport and the corners are the ones the IDE draws.
 *
 * The parameters and the [ScrollPaneScope] content are those of
 * [org.jetbrains.compose.swing.components.layout.ScrollPane]. A corner the IDE installs stands until a child
 * fills that corner, and is back in place when the child leaves.
 *
 * ```
 * val scroll = rememberScrollState()
 * ScrollPane(state = scroll) {
 *     Viewport { LongList() }
 * }
 * ```
 *
 * @see com.intellij.ui.components.JBScrollPane
 * @see com.intellij.ui.dsl.builder.Row.scrollCell
 */
@Composable
@ApiStatus.Experimental
@OptIn(InternalSwingUiApi::class)
public fun ScrollPane(
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ContainerStyleScope> = SwingStyle,
  state: ScrollState = rememberScrollState(),
  @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
  @HorizontalScrollbarPolicy horizontalScrollbar: Int = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
  viewportBorder: Border? = null,
  wheelScrollingEnabled: Boolean = true,
  content: @Composable ScrollPaneScope.() -> Unit,
) {
  ComposeSwingScrollPane(
    factory = { JBScrollPane(verticalScrollbar, horizontalScrollbar) },
    modifier = modifier,
    style = style,
    state = state,
    verticalScrollbar = verticalScrollbar,
    horizontalScrollbar = horizontalScrollbar,
    viewportBorder = viewportBorder,
    wheelScrollingEnabled = wheelScrollingEnabled,
    content = content,
  )
}
