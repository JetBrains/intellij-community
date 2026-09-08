// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
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
    factory = {
      JBOptionButton(null, null).apply {
        // A button reserves room for its focus ring inside its own bounds, and a form aligns a component by the
        // room it reserves, so that painted edges line up rather than bounds. This button reserves none until it
        // is given options - it has no drop-down to draw yet - and options arrive from somewhere slower than the
        // first layout, so the reserve read off the component is whichever it happened to have when the form was
        // last built. Stating one keeps the button still: it stands where it stands whether its options have
        // arrived or not, which is what OptionButtonPlacementTest holds.
        //
        // None is stated rather than the real reserve so that a migrated page stands exactly where the page it
        // replaces stands. A `panel { }` form reads the reserve once, before any options exist, and so places
        // this button as though it reserved nothing; stating nothing reproduces that. It is the reason such a
        // page paints this button a focus width right of every other button on it.
        //
        // TODO Fix this where it comes from - a form re-reading the reserve when a component's insets change -
        //  and then state the real one here instead, on both sides at once.
        putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
      }
    },
    modifier = modifier.actionListener { onClick() },
    update = {
      set(text) { this.text = it }
      set(options) { setOptions(it) }
      set(addSeparator) { this.addSeparator = it }
    },
  )
}
