// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.compose.swing.components.style.ActionLinkKind
import com.intellij.platform.compose.swing.components.style.ActionLinkStyleScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import com.intellij.ui.components.ActionLink as IdeaActionLink
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable

/**
 * @see com.intellij.ui.components.ActionLink
 * @see com.intellij.ui.dsl.builder.Row.link
 */
@Composable
@ApiStatus.Experimental
public fun ActionLink(
  text: @NlsContexts.LinkLabel String,
  onClick: () -> Unit,
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ActionLinkStyleScope> = SwingStyle,
) {
  SwingNode(
    factory = { IdeaActionLink() },
    modifier = modifier.actionListener { onClick() }.styleable(ActionLinkKind, style),
    update = {
      set(text) { this.text = it }
    },
  )
}
