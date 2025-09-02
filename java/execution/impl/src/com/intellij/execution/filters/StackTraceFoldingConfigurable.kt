// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.console.ConsoleOptionsProvider
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.*

internal class StackTraceFoldingConfigurable : UiDslUnnamedConfigurable.Simple(), ConsoleOptionsProvider {

  override fun Panel.createContent() {
    val settings = StackTraceFoldingSettings.getInstance()
    group(ExecutionBundle.message("stack.trace.folding.configurable.name")) {
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
