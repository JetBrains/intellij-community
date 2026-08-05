// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.awt.event.ActionListener
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.event.ChangeListener
import com.intellij.openapi.ui.ComboBox as IdeaComboBox
import com.intellij.ui.dsl.builder.SegmentedButton as IdeaSegmentedButton

/**
 * A row of buttons, one per item, of which one is selected at a time - the control
 * [com.intellij.ui.dsl.builder.Row.segmentedButton] builds.
 *
 * Two components stand behind it and the caller picks neither: a row of buttons while there are at most
 * [maxButtonsCount] items, and a combo box beyond that or while a screen reader is running, which a row of
 * buttons cannot be announced to. Either way the selection is [selectedItem], so it stands across a swap.
 *
 * The selection belongs to the caller: [selectedItem] is what is shown, [onSelectedItemChange] reports what
 * the user chose, and a choice the caller does not adopt is undone on the pass that follows. A
 * [selectedItem] the [items] do not contain shows as no selection.
 *
 * [renderer] gives an item the text it is labelled with, on a button and in the combo box alike; it is read
 * afresh for every item whenever the items or the text it returns for them change.
 *
 * @see com.intellij.ui.dsl.builder.SegmentedButton
 */
@Composable
@ApiStatus.Experimental
public fun <T> SegmentedButton(
  items: List<T>,
  selectedItem: T?,
  onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier = SwingModifier,
  renderer: (T) -> @NlsContexts.Button String,
  maxButtonsCount: Int = IdeaSegmentedButton.DEFAULT_MAX_BUTTONS_COUNT,
) {
  if (screenReaderActive() || items.size > maxButtonsCount) {
    SegmentedComboBox(items, selectedItem, onSelectedItemChange, modifier, renderer)
  }
  else {
    SegmentedButtons(items, selectedItem, onSelectedItemChange, modifier, renderer)
  }
}

/**
 * The row of buttons: the same component the Kotlin UI DSL builds, given the spacing a `panel {}` gives it.
 */
@Composable
private fun <T> SegmentedButtons(
  items: List<T>,
  selectedItem: T?,
  onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier,
  renderer: (T) -> @NlsContexts.Button String,
) {
  val declaredSelection = selectionAmong(items, selectedItem)
  val applied = rememberAppliedValue(declaredSelection)
  val currentItems = rememberUpdatedState(items)
  val currentRenderer = rememberUpdatedState(renderer)
  val currentOnSelectedItemChange = rememberUpdatedState(onSelectedItemChange)
  val listener = remember(applied) {
    ChangeListener { event ->
      val component = event.source as SegmentedButtonComponent<*>
      // The event carries no item, and its source is untyped, so what the component holds is matched
      // against the declared items rather than cast to one of them.
      val selected = currentItems.value.firstOrNull { it == component.selectedItem }
      if (applied.observed(selected)) currentOnSelectedItemChange.value(selected)
    }
  }
  val labels = items.map(renderer)
  SwingNode(
    factory = {
      SegmentedButtonComponent<T> { item ->
        IdeaSegmentedButton.createPresentation(text = currentRenderer.value(item))
      }.apply {
        isOpaque = false
        spacing = IntelliJSpacingConfiguration()
      }
    },
    update = {
      // Presentations are derived when the items are assigned, so the labels ride with them: a renderer
      // that returns new text for items that did not themselves change still refreshes the buttons.
      set(items to labels) { (declaredItems, _) -> this.items = declaredItems }
      declare(declaredSelection, applied, read = { this.selectedItem }, write = { this.selectedItem = it })
      applyModifier(
        modifier.listener<SegmentedButtonComponent<T>, ChangeListener>(
          listener,
          { component, changeListener -> component.addChangeListener(changeListener) },
          { component, changeListener -> component.removeChangeListener(changeListener) },
        )
      )
    },
  )
}

/**
 * The combo box the control falls back to, set up as the Kotlin UI DSL sets its own fallback up: as wide as
 * the widest item, so it takes the room the buttons it stands in for would have taken, and popping up the
 * IDE's list rather than the Swing one.
 */
@Composable
private fun <T> SegmentedComboBox(
  items: List<T>,
  selectedItem: T?,
  onSelectedItemChange: (T?) -> Unit,
  modifier: SwingModifier,
  renderer: (T) -> @NlsContexts.Button String,
) {
  val declaredSelection = selectionAmong(items, selectedItem)
  val applied = rememberAppliedValue(declaredSelection)
  val currentItems = rememberUpdatedState(items)
  val currentRenderer = rememberUpdatedState(renderer)
  val currentOnSelectedItemChange = rememberUpdatedState(onSelectedItemChange)
  val listener = remember(applied) {
    ActionListener { event ->
      val comboBox = event.source as JComboBox<*>
      val selected = currentItems.value.getOrNull(comboBox.selectedIndex)
      if (applied.observed(selected)) currentOnSelectedItemChange.value(selected)
    }
  }
  val labels = items.map(renderer)
  SwingNode(
    factory = {
      IdeaComboBox<T>().apply {
        isSwingPopup = false
        setMinLength(Int.MAX_VALUE)
        // Nothing selected renders as nothing rather than through the renderer, which is only ever
        // handed an item.
        this.renderer = textListCellRenderer<T>("") { item -> currentRenderer.value(item) }
      }
    },
    update = {
      set(items) { declaredItems ->
        // A prebuilt model already carrying the declared selection swaps in silently, where mutating the
        // live model would echo its transient deselection and first-item selection through the listener.
        val newModel = DefaultComboBoxModel(Vector(declaredItems))
        newModel.selectedItem = declaredSelection
        model = newModel
      }
      // The renderer is read as a cell is painted, so text that changed under items that did not needs
      // asking for a repaint.
      set(labels) { repaint() }
      declare(
        declaredSelection,
        applied,
        read = { items.getOrNull(selectedIndex) },
        write = { item -> this.selectedItem = item },
      )
      applyModifier(modifier.actionListener(listener))
    },
  )
}

/**
 * [selectedItem] where [items] holds it, and no selection where they do not.
 *
 * Both components take a selection of their own accord, and the combo box would show one its own popup does
 * not offer, where the row of buttons it stands in for highlights none of them. Neither is a selection the
 * user could make, so neither is declared.
 */
private fun <T> selectionAmong(items: List<T>, selectedItem: T?): T? =
  if (selectedItem != null && selectedItem in items) selectedItem else null

/**
 * Whether a screen reader is running, as composition state, so that turning one on swaps the control that
 * is showing for one a screen reader can announce.
 */
@Composable
private fun screenReaderActive(): Boolean {
  var active by remember { mutableStateOf(ScreenReader.isActive()) }
  DisposableEffect(Unit) {
    val disposable = Disposer.newDisposable("Compose segmented button screen reader listener")
    ScreenReader.addPropertyChangeListener(ScreenReader.SCREEN_READER_ACTIVE_PROPERTY, disposable) { event ->
      active = event.newValue as Boolean
    }
    // The state was seeded before the listener was installed, so a change made in between is taken here.
    active = ScreenReader.isActive()
    onDispose { Disposer.dispose(disposable) }
  }
  return active
}
