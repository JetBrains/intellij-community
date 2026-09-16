// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.components.selection.ListItemScope
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import kotlin.reflect.KClass
import com.intellij.openapi.ui.ComboBox as IdeaComboBox

/**
 * A drop-down of [items] the user picks one of, on the IDE's own combo box rather than a plain
 * `JComboBox`: the popup is as wide as its widest item, and the combo box's minimum size is its
 * preferred size, so a minimum size set through [modifier] does not reach it.
 *
 * The selection is controlled. [selectedItem] is what the combo box shows and [onSelectedItemChange]
 * reports what the user picked, so a pick the caller does not adopt is undone on the pass that follows
 * it. `null` selects nothing.
 *
 * [items] is what can be selected: a [selectedItem] they do not contain selects nothing, and that empty
 * selection is reported through [onSelectedItemChange] - so a caller who drops the selected item out of
 * [items] is told the selection went with it.
 *
 * [itemContent] composes a row of the popup and the closed combo box's display area alike, against a
 * [ListItemScope] naming the row. One composition is reused for every row, so it holds no state of its
 * own. Leaving it out renders the items the way the look and feel does.
 *
 * The combo box is not editable, and this wrapper installs no editor.
 *
 * @see com.intellij.openapi.ui.ComboBox
 * @see com.intellij.ui.dsl.builder.Row.comboBox
 */
@Composable
@ApiStatus.Experimental
public inline fun <reified T : Any> ComboBox(
  items: List<T>,
  selectedItem: T?,
  noinline onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier = SwingModifier,
  noinline itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
  ComboBox(T::class, items, selectedItem, onSelectedItemChange, modifier, itemContent)
}

/**
 * A [ComboBox] for a caller who names [itemType] rather than letting the call site reify it - a generic
 * component of their own, whose item type is a type parameter and so is erased by the time the call is
 * compiled.
 *
 * Otherwise as [ComboBox] taking a reified item type.
 *
 * @param itemType the item type [itemContent] is written over, which every item it is handed is checked
 *   against.
 */
@Composable
@ApiStatus.Experimental
public fun <T : Any> ComboBox(
  itemType: KClass<T>,
  items: List<T>,
  selectedItem: T?,
  onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier = SwingModifier,
  itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
  val mirror = rememberMirrorState(selectedItem)
  SwingNode(
    factory = { IdeaComboBox<T>() },
    modifier = modifier.actionListener<IdeaComboBox<*>> {
      // report narrows the change to the user's own choices: settling the declaration fires an action
      // event of its own, and that one is not a choice.
      mirror.report(this.selectedItemOrNull(), onSelectedItemChange)
    }.then(if (itemContent == null) SwingModifier else SwingModifier.listItemRenderer(rememberListItemRenderer(itemType, itemContent))),
    update = {
      set(items) { newItems ->
        // The items go in as a whole new model rather than into the live one: installing a model raises no
        // action event, where emptying and refilling the live model would echo its own deselection and
        // reselection through the action listener as picks the user never made. The selection the new
        // items leave standing goes in with them, so the combo box is never shown holding another.
        this.model = DefaultComboBoxModel<T>().also { fresh ->
          fresh.addAll(newItems)
          fresh.selectedItem = selectableItem(newItems, selectedItem)
        }
        // The items decide what the combo box can hold, so the mirror follows it here; a selection they
        // dropped is settled against the declaration - and reported - on the pass this invalidates.
        mirror.observed(this.selectedItemOrNull())
      }
      declare(
        value = selectedItem,
        mirror = mirror,
        read = { this.selectedItemOrNull() },
        write = { value -> this.selectedItem = selectableItem(items, value) },
        onSettled = { settled -> onSelectedItemChange(settled) },
      )
    },
  )
}

/**
 * The combo box's selected item, or `null` where it has none.
 *
 * The cast holds because a non-editable combo box only ever selects an element of its own model, and this
 * wrapper installs no editor: what the combo box answers with is one of the [T] items it was given.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> JComboBox<*>.selectedItemOrNull(): T? = selectedItem as T?

/** The element of [items] that [declared] names, or `null` where it names none of them. */
private fun <T> selectableItem(items: List<T>, declared: T?): T? = items.firstOrNull { it == declared }
