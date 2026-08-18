// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.AbstractButton
import com.intellij.ui.components.DropDownLink as IdeaDropDownLink

/**
 * A link that drops a list of [items] down when clicked and stands for the item currently chosen.
 *
 * The selection is controlled: the link holds [selectedItem] and reports the user's picks through
 * [onSelectedItemChange], leaving it to the caller to adopt them. A pick the caller does not adopt is put
 * back on the pass that carries their answer, and a [selectedItem] the caller pushes in is applied without
 * echoing back through the callback.
 *
 * Items are rendered by their `toString`, in the popup and as the link's own text alike, so they carry
 * user-visible text.
 *
 * [items] is read when the popup opens, so the list the composition declares by then is the one the user
 * picks from.
 *
 * @param updateText whether the link's text follows the selection; `false` keeps the text the initial
 *   [selectedItem] gave it, for a link that names what is being chosen rather than the choice.
 * @see com.intellij.ui.components.DropDownLink
 */
@Composable
@ApiStatus.Experimental
public fun <T> DropDownLink(
  items: List<T>,
  selectedItem: T,
  modifier: SwingModifier = SwingModifier,
  updateText: Boolean = true,
  onSelectedItemChange: (T) -> Unit = {},
) {
  val currentItems = rememberUpdatedState(items)
  val callback = rememberUpdatedState(onSelectedItemChange)
  val applied = rememberAppliedValue(selectedItem)
  // The link publishes every selection it settles on, the wrapper's own write-back included; the binding
  // tells the user's picks from that by value.
  val selectionListener = remember(applied) {
    ItemListener { event ->
      if (event.stateChange != ItemEvent.SELECTED) return@ItemListener
      // A link only ever holds an item it was handed - one of the popup's, or one the composition wrote.
      @Suppress("UNCHECKED_CAST")
      val item = event.item as T
      if (applied.observed(item)) callback.value(item)
    }
  }
  val linkText: @Nls String? = if (updateText) itemText(selectedItem) else null
  SwingNode(
    factory = {
      IdeaDropDownLink(selectedItem) { link ->
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(currentItems.value)
          .setRenderer(link.createRenderer())
          // Writing the choice onto the link is the whole of what a pick does; it reaches the
          // composition through the selection channel, as any other move of the selection does.
          .setItemChosenCallback { link.selectedItem = it }
          .createPopup()
      }
    },
    update = {
      declare(selectedItem, applied, read = { this.selectedItem }, write = { this.selectedItem = it })
      // The constructor already put the initial item's text on the link, so only later ones are written;
      // a null text is one the selection does not own and leaves whatever the link shows alone.
      update(linkText) { if (it != null) text = it }
      applyModifier(modifier.itemListener(selectionListener))
    },
  )
}

/** The text an item stands for, on the link and in its popup alike. */
@Nls
private fun itemText(item: Any?): String = item.toString()

/**
 * Attaches [instance] to the link's item-selection channel, where every selection it settles on is
 * published. The channel lives on [AbstractButton], which a `DropDownLink` is one of.
 */
private fun SwingModifier.itemListener(instance: ItemListener): SwingModifier =
  listener<AbstractButton, ItemListener>(
    instance,
    { link, l -> link.addItemListener(l) },
    { link, l -> link.removeItemListener(l) },
  )
