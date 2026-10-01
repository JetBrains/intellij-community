// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.awt.event.FocusEvent
import javax.swing.JCheckBox

private const val SIGNAL_ROW_GAP = 3
private const val SIGNAL_SECTION_GAP = 8

internal fun Panel.soundSignalsGroup(screenReaderSupportCheckbox: JCheckBox) {
  if (!isSoundSignalsFeatureEnabled()) return
  val pending = PendingSoundSignals(screenReaderSupportCheckbox)

  group(IdeBundle.message("sound.signals.group.title")) {
    val signals = getSoundSignals()
    for ((index, signal) in signals.distinctBy { it.group ?: it }.withIndex()) {
      val topGap = if (index == 0) SIGNAL_ROW_GAP else SIGNAL_ROW_GAP + SIGNAL_SECTION_GAP
      val group = signal.group
      when {
        group == null -> signalCheckBox(signal.title, listOf(signal), pending, topGap)
        group.collapsed -> signalCheckBox(group.title, signals.filter { it.group === group }, pending, topGap)
        else -> groupCheckBoxes(group, signals.filter { it.group === group }, pending, topGap)
      }
    }

    screenReaderSupportCheckbox.addItemListener { pending.render() }
    pending.render()

    onReset { pending.reset() }
    onIsModified { pending.isModified() }
    onApply { pending.apply() }
  }
}

private class PendingSoundSignals(private val screenReader: JCheckBox) {
  private val settings = service<AccessibilitySettings>()
  private val views = ArrayList<(SoundSignalsPolicy) -> Unit>()
  private var state = settings.state.soundSignals

  fun policy(): SoundSignalsPolicy = SoundSignalsPolicy(screenReader.isSelected, state)

  fun view(render: (SoundSignalsPolicy) -> Unit) {
    views += render
  }

  fun render() {
    val policy = policy()
    for (view in views) view(policy)
  }

  fun edit(transform: (SoundSignalsSettingsState) -> SoundSignalsSettingsState) {
    state = transform(state)
    render()
  }

  fun reset() {
    state = settings.state.soundSignals
    render()
  }

  fun isModified(): Boolean = state != settings.state.soundSignals

  fun apply() {
    settings.update { it.copy(soundSignals = state) }
  }
}

private fun Panel.groupCheckBoxes(group: SoundSignalGroup, signals: List<SoundSignal>, pending: PendingSoundSignals, topGap: Int) {
  lateinit var groupCheckBox: SoundSignalGroupCheckBox
  lateinit var children: List<JBCheckBox>
  row {
    groupCheckBox = cell(SoundSignalGroupCheckBox(group.title))
      .accessibleDescription(IdeBundle.message("sound.signals.group.accessible.description"))
      .actionListener { _, component ->
        val selected = component.state == ThreeStateCheckBox.State.SELECTED
        pending.edit { it.copy(signals = it.signals + signals.associate { signal -> signal.id to selected }) }
      }
      .component
  }.customize(UnscaledGapsY(top = topGap))
  indent {
    children = signals.map { signal ->
      signalCheckBox(signal.title, listOf(signal), pending, accessibleGroup = group)
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

/** Previews only the first of [signals]. */
private fun Panel.signalCheckBox(
  title: @Nls String,
  signals: List<SoundSignal>,
  pending: PendingSoundSignals,
  topGap: Int = SIGNAL_ROW_GAP,
  accessibleGroup: SoundSignalGroup? = null,
): JBCheckBox {
  val player = SoundSignalPlayer.getInstance()
  val previewSignal = signals.first()
  lateinit var checkBox: JBCheckBox
  row {
    checkBox = checkBox(title)
      .apply {
        accessibleGroup?.let { accessibleDescription(IdeBundle.message("sound.signals.group.member.accessible.description", it.title)) }
      }
      .actionListener { _, component ->
        pending.edit { it.copy(signals = it.signals + signals.associate { signal -> signal.id to component.isSelected }) }
        player.preview(previewSignal)
      }
      .applyToComponent {
        whenFocusGained { e ->
          when (e.cause) {
            FocusEvent.Cause.TRAVERSAL_FORWARD, FocusEvent.Cause.TRAVERSAL_BACKWARD -> player.preview(previewSignal)
            else -> {}
          }
        }
      }
      .component
  }.customize(UnscaledGapsY(top = topGap))
  pending.view { policy -> checkBox.isSelected = signals.all { policy.isSignalOn(it.id) } }
  return checkBox
}

private class SoundSignalGroupCheckBox(text: @Nls String) : ThreeStateCheckBox(text, State.NOT_SELECTED) {
  override fun nextState(): State = if (state == State.SELECTED) State.NOT_SELECTED else State.SELECTED
}
