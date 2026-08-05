// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.intellij.openapi.ui.ComboBox as IdeaComboBox
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.SwingNodeUpdater
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.awt.event.ActionListener
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.ListCellRenderer

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
 * [renderer] draws the popup rows and the closed combo box's display area alike; `null` leaves the items
 * to the renderer the look and feel gave the combo box. A combo box wraps whatever renderer it is given
 * in an adjusting renderer of its own, so what it renders through is never the instance supplied here.
 *
 * The combo box is not editable, and this wrapper installs no editor.
 *
 * @see com.intellij.openapi.ui.ComboBox
 * @see com.intellij.ui.dsl.builder.Row.comboBox
 */
@Composable
@ApiStatus.Experimental
public fun <T> ComboBox(
  items: List<T>,
  selectedItem: T?,
  onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier = SwingModifier,
  renderer: ListCellRenderer<in T>? = null,
) {
  val applied = rememberAppliedValue(selectedItem)
  val currentOnChange = rememberUpdatedState(onSelectedItemChange)
  val listener = remember(applied) {
    ActionListener { event ->
      val picked = (event.source as JComboBox<*>).selectedItemOrNull<T>()
      if (applied.observed(picked)) currentOnChange.value(picked)
    }
  }
  // A combo box has a `selectedItem` of its own, which shadows this parameter inside the update block.
  val declared = selectedItem
  ComboBoxNode(
    modifier = modifier.actionListener(listener),
    renderer = renderer,
  ) {
    set(items) { newItems ->
      // The items go in as a whole new model rather than into the live one: installing a model raises no
      // action event, where emptying and refilling the live model would echo its own deselection and
      // reselection through the action listener as picks the user never made. The selection the new
      // items leave standing goes in with them, so the combo box is never shown holding another.
      this.model = DefaultComboBoxModel<T>().also { fresh ->
        fresh.addAll(newItems)
        fresh.selectedItem = selectableItem(newItems, declared)
      }
      // The items decide what the combo box can hold, so the mirror follows it here; a selection they
      // dropped is settled against the declaration - and reported - on the pass this invalidates.
      applied.observed(this.selectedItemOrNull())
    }
    declare(
      value = declared,
      applied = applied,
      read = { this.selectedItemOrNull() },
      write = { value -> this.selectedItem = selectableItem(items, value) },
      onSettled = { settled -> currentOnChange.value(settled) },
    )
  }
}

/**
 * A [ComboBox] over a caller-owned [model], which owns the items and the selection alike.
 *
 * This overload never writes the [model]: it shows what the model holds and reports every pick through
 * [onSelectedItemChange], leaving the pick itself to be recorded on the model as Swing records it.
 * Supplying a different [model] instance installs that one verbatim.
 *
 * @see ComboBox
 */
@Composable
@ApiStatus.Experimental
public fun <T> ComboBox(
  model: ComboBoxModel<T>,
  onSelectedItemChange: (T?) -> Unit = {},
  modifier: SwingModifier = SwingModifier,
  renderer: ListCellRenderer<in T>? = null,
) {
  val currentOnChange = rememberUpdatedState(onSelectedItemChange)
  val listener = remember {
    ActionListener { event -> currentOnChange.value((event.source as JComboBox<*>).selectedItemOrNull()) }
  }
  ComboBoxNode(
    modifier = modifier.actionListener(listener),
    renderer = renderer,
  ) {
    set(model) { this.model = it }
  }
}

/**
 * The combo box node both overloads render. [modifier] carries the action listener the overload reports
 * picks through, and [installContent] declares what the combo box holds - a list of items with the
 * selection standing on them, or the caller's own model.
 */
@Composable
private fun <T> ComboBoxNode(
  modifier: SwingModifier,
  renderer: ListCellRenderer<in T>?,
  installContent: SwingNodeUpdater<IdeaComboBox<T>>.() -> Unit,
) {
  SwingNode(
    factory = {
      IdeaComboBox<T>().also { comboBox -> ClientProperty.put(comboBox, DEFAULT_RENDERER, comboBox.renderer) }
    },
    update = {
      set(renderer) { supplied ->
        val own = this.defaultRenderer()
        // A combo box still carrying the renderer it was built with has nothing to put back.
        if (supplied != null || this.renderer !== own) this.renderer = supplied ?: own
      }
      installContent()
      applyModifier(modifier)
    },
  )
}

/** Where a combo box keeps the renderer it was built with, so a renderer supplied for it can come off again. */
private val DEFAULT_RENDERER: Key<ListCellRenderer<*>> = Key.create("ComposeSwingComboBox.defaultRenderer")

/**
 * The renderer this combo box was built with: the look and feel's own, already wrapped in the adjusting
 * renderer a combo box puts around whatever it is given. The look and feel builds that renderer in the UI
 * delegate's own factory and exposes it under no key, so the one the combo box was born with is the only
 * one there will be.
 *
 * The cast holds because the renderer stored under the key was read off this very combo box, so it renders
 * the [T] items this one holds.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> IdeaComboBox<T>.defaultRenderer(): ListCellRenderer<in T> =
  ClientProperty.get(this, DEFAULT_RENDERER) as ListCellRenderer<in T>

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
