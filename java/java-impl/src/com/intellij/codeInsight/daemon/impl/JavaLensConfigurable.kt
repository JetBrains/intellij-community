// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.analysis.JavaLensSettings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.java.JavaBundle

class JavaLensConfigurable(val settings: JavaLensSettings) : ImmediateConfigurable {
  override fun createComponent(listener: ChangeListener): javax.swing.JPanel {
    return com.intellij.ui.layout.panel {}
  }

  override val cases: List<ImmediateConfigurable.Case>
    get() = listOf(
      ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.usages"), "usages", { settings.isShowUsages}, { settings.isShowUsages = it}),
      ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.inheritors"), "inheritors", { settings.isShowImplementations}, { settings.isShowImplementations = it}),
      ImmediateConfigurable.Case(JavaBundle.message("project.problems.title"), "related.problems", { settings.isShowRelatedProblems }, { settings.isShowRelatedProblems = it})
    )

  override val mainCheckboxText: String
    get() = JavaBundle.message("settings.inlay.java.show.hints.for")
}
