// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.actions

import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.lang.LangBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel

internal class BackwardDependenciesAdditionalUi {
  val scopeChooserCombo: ScopeChooserCombo = ScopeChooserCombo()
  val panel: DialogPanel = panel {
    row(LangBundle.message("label.scope.to.analyze.usages.in")) {
      cell(scopeChooserCombo)
        .align(AlignX.FILL)
    }
  }
}