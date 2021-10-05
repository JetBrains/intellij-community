// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.actions.ReaderModeSettingsListener
import com.intellij.codeInsight.daemon.impl.analysis.JavaCodeVisionSettings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.java.JavaBundle
import com.intellij.ui.dsl.builder.panel

internal class JavaCodeVisionConfigurable(val settings: JavaCodeVisionSettings) : ImmediateConfigurable {
  override fun createComponent(listener: ChangeListener): javax.swing.JPanel {
    return panel {
      row {
        labelHtml(IdeBundle.message("checkbox.also.in.reader.mode")) {
          ReaderModeSettingsListener.goToEditorReaderMode()
        }
      }
    }
  }

  override val cases: List<ImmediateConfigurable.Case>
    get() = listOf(
      ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.usages"), "usages", { settings.isShowUsages}, { settings.isShowUsages = it}),
      ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.inheritors"), "inheritors", { settings.isShowImplementations}, { settings.isShowImplementations = it})
    )

  override val mainCheckboxText: String
    get() = JavaBundle.message("settings.inlay.java.show.hints.for")
}
