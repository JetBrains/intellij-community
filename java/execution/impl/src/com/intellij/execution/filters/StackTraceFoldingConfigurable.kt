// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected

internal class StackTraceFoldingConfigurable : BoundConfigurable(ExecutionBundle.message("stack.trace.folding.configurable.name")) {

  private val settings = StackTraceFoldingSettings.getInstance()

  override fun createPanel(): DialogPanel {
    return panel {
      group(displayName) {
        row {
          val enabled = checkBox(ExecutionBundle.message("stack.trace.folding.configurable.size.setting.name.prefix"))
            .bindSelected(settings::foldJavaStackTrace)
            .gap(RightGap.SMALL)
          intTextField(0..Int.MAX_VALUE)
            .bindIntText(settings::foldJavaStackTraceGreaterThan)
            .columns(4)
            .enabledIf(enabled.selected)
            .gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization")
          label(ExecutionBundle.message("stack.trace.folding.configurable.size.setting.name.suffix"))
        }
      }
    }
  }
}
