// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import javax.swing.JComponent
import javax.swing.JPanel

class ImplicitTypeInlayProvider : InlayHintsProvider<NoSettings> {
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    val project = file.project
    return object: FactoryInlayHintsCollector(editor) {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element is PsiLocalVariable) {
          val initializer = element.initializer ?: return true
          val identifier = element.identifyingElement ?: return true
          if (initializer is PsiLiteral ||
              initializer is PsiPolyadicExpression ||
              initializer is PsiNewExpression ||
              initializer is PsiTypeCastExpression) {
            return true
          }
          if (!element.typeElement.isInferredType) return true
          val type = element.type
          if (type == PsiPrimitiveType.NULL) return true
          val presentation = JavaTypeHintsPresentationFactory.presentationWithColon(type, factory)
          val withMenu = MenuOnClickPresentation(presentation, project) {
            val provider = this@ImplicitTypeInlayProvider
            listOf(InlayProviderDisablingAction(provider.name, file.language, project, provider.key))
          }
          val shifted = factory.inset(withMenu, left = 3)
          sink.addInlineElement(identifier.textRange.endOffset, true, shifted, false)
        }
        return true
      }
    }
  }

  override fun createSettings(): NoSettings = NoSettings()

  override val name: String
    get() = JavaBundle.message("settings.inlay.java.implicit.types")
  override val group: InlayGroup
    get() = InlayGroup.TYPES_GROUP
  override val key: SettingsKey<NoSettings>
    get() = SettingsKey("java.implicit.types")
  override val previewText: String
    get() = "class HintsDemo {\n" +
            "\n" +
            "    public static void main(String[] args) {\n" +
            "        var list = getList(); // List<String> is inferred\n" +
            "    }\n" +
            "\n" +
            "    private static List<String> getList() {\n" +
            "        return Arrays.asList(\"hello\", \"world\");\n" +
            "    }\n" +
            "}"

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
    return object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent {
        return JPanel()
      }
    }
  }

  override val description: String
    get() = JavaBundle.message("settings.inlay.java.implicit.types.description")
}
