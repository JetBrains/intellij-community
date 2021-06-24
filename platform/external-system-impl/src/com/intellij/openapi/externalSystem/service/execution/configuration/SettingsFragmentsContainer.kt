// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ui.SettingsEditorFragment
import javax.swing.JComponent

class SettingsFragmentsContainer<S> {
  private val fragments = ArrayList<SettingsEditorFragment<S, *>>()

  fun <C : JComponent, F : SettingsEditorFragment<S, out C>> add(fragment: F): F {
    fragments.add(fragment)
    return fragment
  }

  fun <F : SettingsEditorFragment<S, *>> addAll(fragments: Collection<F>): Collection<F> {
    this.fragments.addAll(fragments)
    return fragments
  }

  fun toList(): List<SettingsEditorFragment<S, *>> {
    return fragments
  }

  companion object {
    fun <S> fragments(configure: SettingsFragmentsContainer<S>.() -> Unit): List<SettingsEditorFragment<S, *>> {
      return SettingsFragmentsContainer<S>().apply(configure).toList()
    }
  }
}