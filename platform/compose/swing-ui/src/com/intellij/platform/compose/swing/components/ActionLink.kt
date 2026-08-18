// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.event.ActionListener
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
  val currentOnClick = rememberUpdatedState(onClick)
  val listener = remember { ActionListener { currentOnClick.value() } }
  SwingNode(
    factory = { IdeaActionLink() },
    update = {
      set(text) { this.text = it }
      applyModifier(modifier.actionListener(listener))
    },
  )
}
