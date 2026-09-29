// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.awt.event.FocusEvent

private const val SIGNAL_ROW_GAP = 3
private const val SIGNAL_SECTION_GAP = 8

internal fun Panel.soundSignalsGroup() {
  if (!isSoundSignalsFeatureEnabled()) return
  val settings = service<AccessibilitySettings>()

  group(IdeBundle.message("sound.signals.group.title")) {
    lateinit var mode: ComboBox<SoundSignalsMode>
    row(IdeBundle.message("sound.signals.mode.label")) {
      mode = comboBox(SoundSignalsMode.entries, textListCellRenderer("") { it.title })
        .bindItem({ settings.state.soundSignals.mode }, { mode -> mode?.let { settings.updateSoundSignals { it.copy(mode = mode) } } })
        .component
    }
    rowsRange {
      val signals = getSoundSignals()
      for ((index, signal) in signals.distinctBy { it.group ?: it }.withIndex()) {
        val topGap = if (index == 0) SIGNAL_ROW_GAP else SIGNAL_ROW_GAP + SIGNAL_SECTION_GAP
        val group = signal.group
        if (group == null) {
          signalCheckBox(signal, settings, topGap)
        }
        else {
          groupCheckBoxes(group, signals.filter { it.group === group }, settings, topGap)
        }
      }
    }.enabledIf(mode.selectedValueMatches { it != SoundSignalsMode.OFF })
  }
}

private fun Panel.groupCheckBoxes(group: SoundSignalGroup, signals: List<SoundSignal>, settings: AccessibilitySettings, topGap: Int) {
  lateinit var groupCheckBox: SoundSignalGroupCheckBox
  lateinit var children: List<JBCheckBox>
  row {
    groupCheckBox = cell(SoundSignalGroupCheckBox(group.title))
      .accessibleDescription(IdeBundle.message("sound.signals.group.accessible.description"))
      .actionListener { _, component ->
        val selected = component.state == ThreeStateCheckBox.State.SELECTED
        for (child in children) child.isSelected = selected
      }
      .component
  }.customize(UnscaledGapsY(top = topGap))
  indent {
    children = signals.map { signalCheckBox(it, settings) }
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

private fun Panel.signalCheckBox(signal: SoundSignal, settings: AccessibilitySettings, topGap: Int = SIGNAL_ROW_GAP): JBCheckBox {
  val player = SoundSignalPlayer.getInstance()
  lateinit var checkBox: JBCheckBox
  row {
    checkBox = checkBox(signal.title)
      .apply {
        signal.group?.let { accessibleDescription(IdeBundle.message("sound.signals.group.member.accessible.description", it.title)) }
      }
      .bindSelected(
        { signal.id !in settings.state.soundSignals.disabledSignals },
        { checked -> settings.setSignalEnabled(signal, checked) },
      )
      .actionListener { _, _ -> player.preview(signal) }
      .applyToComponent {
        whenFocusGained { e ->
          when (e.cause) {
            FocusEvent.Cause.TRAVERSAL_FORWARD, FocusEvent.Cause.TRAVERSAL_BACKWARD -> player.preview(signal)
            else -> {}
          }
        }
      }
      .component
  }.customize(UnscaledGapsY(top = topGap))
  return checkBox
}

private class SoundSignalGroupCheckBox(text: @Nls String) : ThreeStateCheckBox(text, State.NOT_SELECTED) {
  override fun nextState(): State = if (state == State.SELECTED) State.NOT_SELECTED else State.SELECTED
}

private fun AccessibilitySettings.setSignalEnabled(signal: SoundSignal, enabled: Boolean) {
  updateSoundSignals {
    it.copy(disabledSignals = if (enabled) it.disabledSignals - signal.id else it.disabledSignals + signal.id)
  }
}

private fun AccessibilitySettings.updateSoundSignals(function: (SoundSignalsSettingsState) -> SoundSignalsSettingsState) {
  update { it.copy(soundSignals = function(it.soundSignals)) }
}
