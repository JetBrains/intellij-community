// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import com.intellij.ui.components.ActionLink as IdeaActionLink

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
) {
  SwingNode(
    factory = { IdeaActionLink() },
    modifier = modifier.actionListener { onClick() },
    update = {
      set(text) { this.text = it }
    },
  )
}
