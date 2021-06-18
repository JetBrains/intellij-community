// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.SettingsEditorFragment
import javax.swing.JComponent

class SettingsFragmentsContainer<S : RunConfigurationBase<*>> {
  private val fragments = ArrayList<SettingsEditorFragment<S, *>>()

  fun <C : JComponent> add(fragment: SettingsEditorFragment<S, C>): SettingsEditorFragment<S, C> {
    fragments.add(fragment)
    return fragment
  }

  fun addAll(fragments: Collection<SettingsEditorFragment<S, *>>): Collection<SettingsEditorFragment<S, *>> {
    this.fragments.addAll(fragments)
    return fragments
  }

  fun toList(): List<SettingsEditorFragment<S, *>> {
    return fragments
  }

  companion object {
    fun <S : RunConfigurationBase<*>> fragments(configure: SettingsFragmentsContainer<S>.() -> Unit): List<SettingsEditorFragment<S, *>> {
      return SettingsFragmentsContainer<S>().apply(configure).toList()
    }
  }
}