// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.parameter.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.ui.layout.*
import com.intellij.util.PlatformIcons
import javax.swing.JComponent
import javax.swing.JLabel

class NewJavaParameterHintsProvider : NewParameterHintsProvider<NoSettings> {
  override fun getCollector(element: PsiElement, settings: NoSettings, editor: Editor): ParameterHintsCollector<NoSettings> {
    return object: FactoryHintsCollector<NoSettings>(editor) {
      override fun getParameterHints(element: PsiElement, settings: NoSettings, sink: ParameterHintsSink) {
        if (element.text == "Hello" && element.children.isEmpty()) {
          val presentation = factory.icon(PlatformIcons.CHECK_ICON)
          val info = ParameterHintInfo(presentation, element.textOffset, false, false, null, null)
          sink.addHint(info)
        }
      }
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
