// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import javax.swing.JComponent
import javax.swing.JPanel

class ImplicitTypeInlayProvider : InlayHintsProvider<NoSettings> {
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    return object: FactoryInlayHintsCollector(editor) {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element is PsiLocalVariable) {
          val initializer = element.initializer ?: return true
          val identifier = element.identifyingElement ?: return true
          if (initializer is PsiLiteral || initializer is PsiPolyadicExpression || initializer is PsiNewExpression) return true
          if (element.typeElement.isInferredType) {
            val presentation = JavaTypeHintsPresentationFactory.presentationWithColon(element.type, factory)
            val shifted = factory.inset(presentation, left = 3)
            sink.addInlineElement(identifier.textRange.endOffset, true, shifted)
          }
        }
        return true
      }
    }
  }

  override fun createSettings(): NoSettings = NoSettings()

  override val name: String
    get() = JavaBundle.message("settings.inlay.java.implicit.types")
  override val key: SettingsKey<NoSettings>
    get() = SettingsKey("java.implicit.types")
  override val previewText: String?
    get() = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
    return object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent {
        return JPanel()
      }
    }
  }
}