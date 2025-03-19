// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration.fragments

import com.intellij.execution.ui.SettingsEditorFragment
import javax.swing.JComponent

class SettingsEditorFragmentContainer<S> {
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
    fun <S> fragments(configure: SettingsEditorFragmentContainer<S>.() -> Unit): List<SettingsEditorFragment<S, *>> {
      return SettingsEditorFragmentContainer<S>().apply(configure).toList()
    }
  }
}