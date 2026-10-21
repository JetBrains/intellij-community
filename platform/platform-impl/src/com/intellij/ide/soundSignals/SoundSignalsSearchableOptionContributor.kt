// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.ACCESSIBILITY_CONFIGURABLE_ID
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor

internal class SoundSignalsSearchableOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    if (!isSoundSignalsFeatureEnabled()) return
    val displayName = IdeBundle.message("configurable.AccessibilityConfigurable.display.name")
    fun add(text: String, hit: String) =
      processor.addOptions(text, null, hit, ACCESSIBILITY_CONFIGURABLE_ID, displayName, true)

    val groupTitle = IdeBundle.message("sound.signals.group.title")
    add(groupTitle, groupTitle)
    add(IdeBundle.message("sound.signals.search.keywords"), groupTitle)
    val signals = getSoundSignals().filter(::isShownInSettings)
    for (group in signals.mapNotNullTo(LinkedHashSet()) { it.group }) {
      add(group.title, group.title)
    }
    for (signal in signals) {
      add(signal.title, signal.title)
    }
  }
}
