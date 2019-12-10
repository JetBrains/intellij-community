// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable

class JavaLensConfigurable(val settings: JavaLensSettings) : ImmediateConfigurable {
  override fun createComponent(listener: ChangeListener): javax.swing.JPanel {
    return com.intellij.ui.layout.panel {}
  }

  override val cases: List<ImmediateConfigurable.Case>
    get() = listOf(
      ImmediateConfigurable.Case("Usages", "usages", { settings.isShowUsages}, { settings.isShowUsages = it}),
      ImmediateConfigurable.Case("Inheritors", "inheritors", { settings.isShowImplementations}, { settings.isShowImplementations = it})
    )

  override val mainCheckboxText: String
    get() = "Show hints for:"
}
