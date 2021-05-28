// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.actions

import com.intellij.ui.layout.*
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.lang.LangBundle

internal class AdditionalSettingsPanelUi {
  val scopeChooserCombo: ScopeChooserCombo = ScopeChooserCombo()
  val panel = panel {
    titledRow(LangBundle.message("separator.title.scope.to.analyze.usages.in")) {
      row {
        scopeChooserCombo()
      }
    }
  }
}