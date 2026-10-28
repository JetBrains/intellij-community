// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import com.intellij.notification.impl.boundGroupIds
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.paint.useCopy
import com.intellij.ui.soundPreviewButton
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.event.AncestorEvent

internal fun Panel.soundSignalsGroup(screenReaderSupportCheckbox: JCheckBox) {
  if (!isSoundSignalsFeatureEnabled()) return
  val pending = PendingSoundSignals(screenReaderSupportCheckbox::isSelected)

  group(IdeBundle.message("sound.signals.group.title")) {
    val signals = getSoundSignals().filter(::isShownInSettings)
    for ((index, signal) in signals.distinctBy { it.group ?: it }.withIndex()) {
      val addTopGap = index != 0
      val group = signal.group
      when {
        group == null -> signalCheckBox(signal, pending, addTopGap)
        else -> groupCheckBoxes(group, signals.filter { it.group === group }, pending, addTopGap)
      }
    }

    screenReaderSupportCheckbox.addItemListener { pending.render() }
    screenReaderSupportCheckbox.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent) = pending.render()
    })
    pending.render()

    onReset { pending.reset() }
    onIsModified { pending.isModified() }
    onApply { pending.apply() }
  }
}

internal fun isShownInSettings(signal: SoundSignal): Boolean =
  signal.group !== IdeSoundSignals.EVENTS_GROUP || boundGroupIds(signal.id).isNotEmpty()

private fun Panel.groupCheckBoxes(group: SoundSignalGroup, signals: List<SoundSignal>, pending: PendingSoundSignals, addTopGap: Boolean) {
  val groupCheckBox = SoundSignalGroupCheckBox(group.title)
  lateinit var children: List<JBCheckBox>
  soundSignalRow(groupCheckBox, addTopGap) {
    cell(groupCheckBox)
      .accessibleDescription(IdeBundle.message("sound.signals.group.accessible.description"))
      .actionListener { _, component ->
        val selected = component.state == ThreeStateCheckBox.State.SELECTED
        pending.choose(signals.associate { it.id to selected })
      }
  }
  indent {
    children = signals.map { signal ->
      signalCheckBox(signal, pending, accessibleGroup = group)
    }
  }
  fun updateGroupState() {
    groupCheckBox.state = when (children.count { it.isSelected }) {
      0 -> ThreeStateCheckBox.State.NOT_SELECTED
      children.size -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }
  updateGroupState()
  for (child in children) {
    child.addItemListener { updateGroupState() }
  }
}

private fun Panel.signalCheckBox(
  signal: SoundSignal,
  pending: PendingSoundSignals,
  addTopGap: Boolean = false,
  accessibleGroup: SoundSignalGroup? = null,
): JBCheckBox {
  val checkBox = JBCheckBox(signal.title)
  soundSignalRow(checkBox, addTopGap) {
    cell(checkBox)
      .resizableColumn()
      .apply {
        accessibleGroup?.let { accessibleDescription(IdeBundle.message("sound.signals.group.member.accessible.description", it.title)) }
      }
      .actionListener { _, component -> pending.choose(mapOf(signal.id to component.isSelected)) }
    soundPreviewButton { SoundSignalPlayer.getInstance().preview(signal) }
      .accessibleName(IdeBundle.message("sound.preview.button.accessible.name", signal.title))
  }
  pending.view { policy -> checkBox.isSelected = policy.isSignalOn(signal.id) }
  return checkBox
}

private fun Panel.soundSignalRow(checkBox: JCheckBox, addTopGap: Boolean, init: Row.() -> Unit) {
  checkBox.isOpaque = false
  val rowPanel = SoundSignalRowPanel(com.intellij.ui.dsl.builder.panel { row(init = init) })
  object : ClickListener() {
    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      if (event.button != MouseEvent.BUTTON1) return false
      checkBox.requestFocusInWindow()
      checkBox.doClick()
      return true
    }
  }.installOn(rowPanel)
  row {
    cell(rowPanel).align(AlignX.FILL)
    cell() // Keep the row panel in the first column so the empty column takes the extra width.
    if (addTopGap) {
      topGap(TopGap.SMALL)
    }
  }.layout(RowLayout.PARENT_GRID)
}

private class SoundSignalRowPanel(content: DialogPanel) : JPanel(GridLayout()) {
  private var hovered = false

  init {
    isOpaque = false
    border = JBUI.Borders.empty(0, HOVER_INSET_LEFT, 0, HOVER_INSET_RIGHT)
    content.isOpaque = false
    RowsGridBuilder(this).cell(content, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
    object : HoverStateListener() {
      override fun hoverChanged(component: Component, hovered: Boolean) {
        this@SoundSignalRowPanel.hovered = hovered
        repaint()
      }
    }.addTo(this)
  }

  override fun paintComponent(g: Graphics) {
    if (!hovered) return
    g.useCopy { g2 ->
      GraphicsUtil.setupAAPainting(g2)
      g2.color = JBUI.CurrentTheme.List.Hover.background(false)
      val arc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      // The row is 1 px taller than the checkbox and the preview button need, and this 1px is put below them, so subtract it.
      g2.fillRoundRect(0, 0, width, height - 1, arc, arc)
    }
  }
}

private class SoundSignalGroupCheckBox(text: @Nls String) : ThreeStateCheckBox(text, State.NOT_SELECTED) {
  override fun nextState(): State = if (state == State.SELECTED) State.NOT_SELECTED else State.SELECTED
}

private const val HOVER_INSET_LEFT = 6
private const val HOVER_INSET_RIGHT = 3
