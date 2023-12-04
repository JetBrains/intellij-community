// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip.onboarding

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property

@State(name = "InlineCompletionOnboarding", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
@Service(Service.Level.APP)
internal class InlineCompletionOnboardingComponent : PersistentStateComponent<InlineCompletionOnboardingComponent> {

  private var shownTimeMs = 0L

  @Property
  private var onboardingFinished = false

  override fun getState(): InlineCompletionOnboardingComponent {
    return this
  }

  override fun loadState(state: InlineCompletionOnboardingComponent) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun shouldExplicitlyDisplayTooltip(): Boolean {
    return !onboardingFinished
  }

  fun fireOnboardingFinished() {
    onboardingFinished = true
  }

  fun fireTooltipLivedFor(ms: Long) {
    if (shouldExplicitlyDisplayTooltip()) {
      shownTimeMs += ms
      if (shownTimeMs >= MAX_SHOWN_TIME_MS) {
        fireOnboardingFinished()
      }
    }
  }

  companion object {
    private const val MAX_SHOWN_TIME_MS = 10_000L

    fun getInstance(): InlineCompletionOnboardingComponent = service()
  }
}
