// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property

@State(name = "InlineCompletionOnboarding", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
@Service(Service.Level.APP)
internal class InlineCompletionOnboardingComponent : PersistentStateComponent<InlineCompletionOnboardingComponent> {

  @Property
  private var shownTimes = 0

  @Property
  private var tooltipUsedExplicitly = false

  override fun getState(): InlineCompletionOnboardingComponent {
    return this
  }

  override fun loadState(state: InlineCompletionOnboardingComponent) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun shouldDisplayTooltip(): Boolean {
    return !tooltipUsedExplicitly && shownTimes < MAX_SHOW_TIMES
  }

  fun fireTooltipUsed() {
    tooltipUsedExplicitly = true
  }

  fun fireTooltipShown() {
    shownTimes = minOf(shownTimes + 1, MAX_SHOW_TIMES)
  }

  companion object {
    private const val MAX_SHOW_TIMES = 6

    fun getInstance(): InlineCompletionOnboardingComponent = service()
  }
}
