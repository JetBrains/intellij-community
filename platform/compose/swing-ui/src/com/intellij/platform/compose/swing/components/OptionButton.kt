// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode

/**
 * A button whose primary click runs [onClick] and whose drop-down offers the secondary [options].
 *
 * @param addSeparator whether the drop-down shows a separator, mirroring [JBOptionButton.addSeparator].
 * @see com.intellij.ui.components.JBOptionButton
 */
@Composable
@ApiStatus.Experimental
public fun OptionButton(
  text: @NlsContexts.Button String,
  onClick: () -> Unit,
  modifier: SwingModifier = SwingModifier,
  options: List<AnAction> = emptyList(),
  addSeparator: Boolean = true,
) {
  SwingNode(
    factory = { JBOptionButton(null, null) },
    modifier = modifier.actionListener { onClick() },
    update = {
      set(text) { this.text = it }
      set(options) { setOptions(it) }
      set(addSeparator) { this.addSeparator = it }
    },
  )
}
