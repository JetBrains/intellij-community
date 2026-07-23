// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.ActionLink as IdeaActionLink
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import java.awt.event.ActionListener

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
