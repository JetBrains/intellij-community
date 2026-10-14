// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import com.intellij.notification.impl.boundGroupIds
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.soundPreviewButton
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox
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
    soundPreviewButton { SoundSignalPlayer.getInstance().preview(signal) }
      .accessibleName(IdeBundle.message("sound.preview.button.accessible.name", signal.title))
    if (addTopGap) {
      topGap(TopGap.SMALL)
    }
  }.layout(RowLayout.PARENT_GRID)
  pending.view { policy -> checkBox.isSelected = policy.isSignalOn(signal.id) }
  return checkBox
}

private class SoundSignalGroupCheckBox(text: @Nls String) : ThreeStateCheckBox(text, State.NOT_SELECTED) {
  override fun nextState(): State = if (state == State.SELECTED) State.NOT_SELECTED else State.SELECTED
}
