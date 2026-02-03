// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration.fragments

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JComponent

class SettingsEditorLabeledComponent<C : JComponent>(label: @NlsContexts.Label String, component: C) : LabeledComponent<C>() {

  fun modifyComponentSize(configure: C.() -> Unit) {
    layout = WrapLayout(FlowLayout.LEADING, UIUtil.DEFAULT_HGAP, 2)
    border = JBUI.Borders.emptyLeft(-UIUtil.DEFAULT_HGAP)
    component.configure()
  }

  init {
    text = label
    labelLocation = BorderLayout.WEST
    setComponent(component)
  }

  companion object {

    fun <S, C : JComponent, F : SettingsEditorFragment<S, SettingsEditorLabeledComponent<C>>> F.modifyLabeledComponentSize(
      configure: C.() -> Unit
    ): F = apply {
      component().modifyComponentSize(configure)
    }
  }
}