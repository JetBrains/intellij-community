// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.parameter.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JLabel

class NewJavaParameterHintsProvider : NewParameterHintsProvider<NoSettings> {
  override fun getParameterHints(element: PsiElement, settings: NoSettings, factory: PresentationFactory, sink: ParameterHintsSink) {
    if (element.text == "Hello") {
      val info = ParameterHintInfo(factory.roundedText(" world"), element.textOffset, false, false, null, null)
      sink.addHint(info)
    }
  }

  override val settingsKey: SettingsKey<ParameterHintsSettings<NoSettings>>
    get() = SettingsKey("java.test.hints")
  override val preview: String
    get() = "Test!"

  override fun createSettings(): NoSettings = NoSettings()

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object: ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent {
      return panel {
        this.row {
          JLabel("Settings")()
        }
      }
    }
  }

  override val blackList: BlackListConfiguration? = null

}
