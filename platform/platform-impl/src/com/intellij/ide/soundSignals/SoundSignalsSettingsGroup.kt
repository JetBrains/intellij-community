// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.notification.impl.boundGroupIds
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.JComponent
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

private fun isShownInSettings(signal: SoundSignal): Boolean =
  signal.group !== IdeSoundSignals.EVENTS_GROUP || boundGroupIds(signal.id).isNotEmpty()

private fun Panel.groupCheckBoxes(group: SoundSignalGroup, signals: List<SoundSignal>, pending: PendingSoundSignals, addTopGap: Boolean) {
  lateinit var groupCheckBox: SoundSignalGroupCheckBox
  lateinit var children: List<JBCheckBox>
  row {
    groupCheckBox = cell(SoundSignalGroupCheckBox(group.title))
      .accessibleDescription(IdeBundle.message("sound.signals.group.accessible.description"))
      .actionListener { _, component ->
        val selected = component.state == ThreeStateCheckBox.State.SELECTED
        pending.choose(signals.associate { it.id to selected })
      }
      .component
    if (addTopGap) {
      topGap(TopGap.SMALL)
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
  lateinit var checkBox: JBCheckBox
  row {
    checkBox = checkBox(signal.title)
      .apply {
        accessibleGroup?.let { accessibleDescription(IdeBundle.message("sound.signals.group.member.accessible.description", it.title)) }
      }
      .actionListener { _, component -> pending.choose(mapOf(signal.id to component.isSelected)) }
      .component
    actionButton(PreviewSoundSignalAction(signal))
      .applyToComponent {
        isFocusable = true
        border = JBUI.Borders.empty(DarculaUIUtil.BW.unscaled.toInt() + FOCUS_RING_GAP)
        setLook(PreviewButtonLook)
        // The button is taller than a checkbox, so with the default gaps it would make the row taller
        putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.NONE)
      }
    if (addTopGap) {
      topGap(TopGap.SMALL)
    }
  }.layout(RowLayout.PARENT_GRID)
  pending.view { policy -> checkBox.isSelected = policy.isSignalOn(signal.id) }
  return checkBox
}

private class PreviewSoundSignalAction(private val signal: SoundSignal) :
  DumbAwareAction(IdeBundle.message("sound.signals.preview.button"), null, AllIcons.Actions.PlaySound) {
  override fun actionPerformed(e: AnActionEvent) {
    SoundSignalPlayer.getInstance().preview(signal)
  }
}

private const val FOCUS_RING_GAP = 1

private object PreviewButtonLook : IdeaActionButtonLook() {
  override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
    if (state != ActionButtonComponent.SELECTED) super.paintBackground(g, component, state)
  }

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    if (!component.isFocusOwner) {
      super.paintBorder(g, component, state)
      return
    }
    val bw = DarculaUIUtil.BW.get()
    val gap = JBUI.scale(FOCUS_RING_GAP)
    val rect = Rectangle(component.size)
    JBInsets.removeFrom(rect, component.insets)
    rect.grow(gap + bw, gap + bw)
    val arc = buttonArc.float + 2 * (gap + bw)
    DarculaNewUIUtil.drawRoundedRectangle(g, rect, JBUI.CurrentTheme.Focus.focusColor(), arc, bw.toFloat())
  }
}

private class SoundSignalGroupCheckBox(text: @Nls String) : ThreeStateCheckBox(text, State.NOT_SELECTED) {
  override fun nextState(): State = if (state == State.SELECTED) State.NOT_SELECTED else State.SELECTED
}
