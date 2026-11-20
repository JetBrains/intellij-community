// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.compose.swing.components.style.OptionButtonKind
import com.intellij.ui.components.JBOptionButton
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

/**
 * A button whose main part runs [onClick] and whose drop-down offers the secondary [options].
 *
 * @param enabled whether the main part and the drop-down accept clicks.
 * @param addSeparator whether the drop-down shows a separator, mirroring [JBOptionButton.addSeparator].
 * @see com.intellij.ui.components.JBOptionButton
 */
@Composable
@ApiStatus.Experimental
public fun OptionButton(
  text: @NlsContexts.Button String,
  onClick: () -> Unit,
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ButtonStyleScope> = SwingStyle,
  enabled: Boolean = true,
  options: List<AnAction> = emptyList(),
  addSeparator: Boolean = true,
) {
  SwingNode(
    factory = { JBOptionButton(null, null) },
    modifier = modifier.then(MainActionElement(text, enabled, onClick)).styleable(OptionButtonKind, style),
    update = {
      set(options) { setOptions(it) }
      set(addSeparator) { this.addSeparator = it }
    },
  )
}

/**
 * Declares the main action of an [OptionButton]: its text, its enabled state and what a click on it runs.
 *
 * The look and feel lays the button out as two halves, and the main half runs, names and enables itself from the
 * button's action, again whenever a new UI builds the halves.
 */
private data class MainActionElement(
  private val text: String,
  private val enabled: Boolean,
  private val onClick: () -> Unit,
) : SwingModifier.NodeElement<JBOptionButton, MainActionNode>() {
  override val name: String get() = "mainAction"

  override val declaredValues: Map<String, Any?> get() = mapOf("text" to text, "enabled" to enabled)

  override val targetType: Class<JBOptionButton> get() = JBOptionButton::class.java

  override fun create(): MainActionNode = MainActionNode()

  override fun update(node: MainActionNode) {
    node.action.putValue(Action.NAME, text)
    node.action.isEnabled = enabled
    node.onClick = onClick
  }
}

private class MainActionNode : SwingModifier.ComponentNode<JBOptionButton>() {
  var onClick: () -> Unit = {}

  val action: Action = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) = onClick()
  }

  override fun onAttach() {
    component.action = action
  }

  override fun onDetach() {
    component.action = null
  }
}
