// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import kotlin.reflect.KMutableProperty0

open class BuildSystemButton(val buildSystemType: BuildSystemType, settings: KMutableProperty0<String>) : WizardToggleButton(
  buildSystemType.name, settings)

open class LanguageButton(val language: String, override val settings: KMutableProperty0<String>) : WizardToggleButton(language, settings)

abstract class WizardToggleButton(val button: String, open val settings: KMutableProperty0<String>) : ToggleAction(button) {
  init {
    templatePresentation.text = button
  }

  override fun displayTextInToolbar() = true
  override fun isSelected(e: AnActionEvent) = button === settings.get()
  override fun setSelected(e: AnActionEvent, state: Boolean) = run { if (state) settings.set(button) }

  fun setSelected(state: Boolean) = run { setSelected(ActionUtil.createEmptyEvent(), state) }
  fun isSelected() = button === settings.get()
}