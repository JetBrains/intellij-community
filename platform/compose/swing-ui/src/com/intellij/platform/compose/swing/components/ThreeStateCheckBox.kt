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
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import com.intellij.util.ui.ThreeStateCheckBox as IdeaThreeStateCheckBox

/** The state of a [ThreeStateCheckBox]. */
@ApiStatus.Experimental
public enum class ThreeStateCheckBoxState {
  SELECTED,
  NOT_SELECTED,
  INDETERMINATE,
}

/**
 * A checkbox carrying a third, indeterminate state. A click moves it one step along
 * `NOT_SELECTED -> INDETERMINATE -> SELECTED -> NOT_SELECTED`.
 *
 * The box shows whatever [state] declares, and [onStateChange] is handed the state a click moved it to. A
 * click the caller does not adopt is put back, and a state the caller pushes in is written onto the box
 * without being reported back through [onStateChange].
 *
 * @see com.intellij.util.ui.ThreeStateCheckBox
 * @see com.intellij.ui.dsl.builder.Row.threeStateCheckBox
 */
@Composable
@ApiStatus.Experimental
public fun ThreeStateCheckBox(
  text: @NlsContexts.Checkbox String,
  state: ThreeStateCheckBoxState,
  modifier: SwingModifier = SwingModifier,
  onStateChange: (ThreeStateCheckBoxState) -> Unit = {},
) {
  val currentOnStateChange = rememberUpdatedState(onStateChange)
  val applied = rememberAppliedValue(state)
  // The box publishes its state for every cycle, the user's click and this wrapper's own write alike. The
  // binding answers which is which by value: a cycle that lands on the declaration is the declaration
  // arriving.
  val listener = remember(applied) {
    ActionListener { event ->
      val cycled = (event.source as IdeaThreeStateCheckBox).state.toCheckBoxState()
      if (applied.observed(cycled)) currentOnStateChange.value(cycled)
    }
  }
  SwingNode(
    factory = { IdeaThreeStateCheckBox() },
    update = {
      set(text) { this.text = it }
      // Settled against the box rather than applied on change: a click moves the box out from under the
      // declaration, and a declaration equal to the last one still has to stand.
      declare(
        value = state,
        applied = applied,
        read = { this.state.toCheckBoxState() },
        write = { this.state = it.toIdeaState() },
      )
      applyModifier(modifier.actionListener(listener))
    },
  )
}

private fun ThreeStateCheckBoxState.toIdeaState(): IdeaThreeStateCheckBox.State =
  when (this) {
    ThreeStateCheckBoxState.SELECTED -> IdeaThreeStateCheckBox.State.SELECTED
    ThreeStateCheckBoxState.NOT_SELECTED -> IdeaThreeStateCheckBox.State.NOT_SELECTED
    ThreeStateCheckBoxState.INDETERMINATE -> IdeaThreeStateCheckBox.State.DONT_CARE
  }

private fun IdeaThreeStateCheckBox.State.toCheckBoxState(): ThreeStateCheckBoxState =
  when (this) {
    IdeaThreeStateCheckBox.State.SELECTED -> ThreeStateCheckBoxState.SELECTED
    IdeaThreeStateCheckBox.State.NOT_SELECTED -> ThreeStateCheckBoxState.NOT_SELECTED
    IdeaThreeStateCheckBox.State.DONT_CARE -> ThreeStateCheckBoxState.INDETERMINATE
  }
