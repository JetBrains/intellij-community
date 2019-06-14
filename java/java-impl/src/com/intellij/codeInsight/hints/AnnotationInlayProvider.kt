// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.MakeInferredAnnotationExplicit
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

class AnnotationInlayProvider : InlayHintsProvider<AnnotationInlayProvider.Settings> {
  override fun getCollectorFor(file: PsiFile,
                               editor: Editor,
                               settings: Settings,
                               sink: InlayHintsSink): InlayHintsCollector? {
    val project = file.project
    return object : FactoryInlayHintsCollector(editor) {
      val presentations = mutableListOf<InlayPresentation>()

      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (element is PsiModifierListOwner) {
          var annotations = emptySequence<PsiAnnotation>()
          if (settings.showExternal) {
            annotations += ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(element).orEmpty()
          }
          if (settings.showInferred) {
            annotations += InferredAnnotationsManager.getInstance(project).findInferredAnnotations(element)
          }

          val shownAnnotations = mutableSetOf<String>()
          annotations.forEach {
            val nameReferenceElement = it.nameReferenceElement
            if (nameReferenceElement != null && element.modifierList != null &&
                (shownAnnotations.add(nameReferenceElement.qualifiedName) || JavaDocInfoGenerator.isRepeatableAnnotationType(it))) {
              presentations.add(createPresentation(it, element))
            }
          }
          val offset = element.modifierList!!.textRange.startOffset
          if (presentations.isNotEmpty()) {
            sink.addInlineElement(offset, false, SequencePresentation(SmartList(presentations)))
          }
          presentations.clear()
        }
        return true
      }

      private fun createPresentation(
        annotation: PsiAnnotation,
        element: PsiModifierListOwner
      ): MenuOnClickPresentation {
        val presentation = annotationPresentation(annotation)
        return MenuOnClickPresentation(presentation, project) {
          val makeExplicit = InsertAnnotationAction(project, file, element)
          listOf(
            makeExplicit,
            ToggleSettingsAction("Turn off external annotations", settings::showExternal),
            ToggleSettingsAction("Turn off inferred annotations", settings::showInferred)
          )
        }
      }

      private fun annotationPresentation(annotation: PsiAnnotation): InsetPresentation = with(factory) {
        val nameReferenceElement = annotation.nameReferenceElement
        val parameterList = annotation.parameterList
        inset(
          roundWithBackground(seq(
            smallText("@"),
            psiSingleReference(smallText(nameReferenceElement?.referenceName ?: "")) { nameReferenceElement?.resolve() },
            parametersPresentation(parameterList)
          )),
          left = 1,
          right = 1
        )

      }

      private fun parametersPresentation(parameterList: PsiAnnotationParameterList) = with(factory) {
        val attributes = parameterList.attributes
        when {
          attributes.isEmpty() -> smallText("()")
          else -> insideParametersPresentation(attributes, collapsed = parameterList.textLength > 30)
        }
      }

      private fun insideParametersPresentation(attributes: Array<PsiNameValuePair>, collapsed: Boolean) = with(factory) {
        collapsible(
          smallText("("),
          smallText("..."),
          {
            join(
              presentations = attributes.map { pairPresentation(it) },
              separator = { smallText(", ") }
            )
          },
          smallText(")"),
          collapsed
        )
      }

      private fun pairPresentation(attribute: PsiNameValuePair) = with(factory) {
        seq(
          psiSingleReference(smallText(attribute.name ?: ""), resolve = { attribute.reference?.resolve() }),
          smallText(" = "),
          smallText(attribute.value?.text ?: "")
        )
      }
    }
  }

  override fun createSettings(): Settings = Settings(showInferred = true, showExternal = true)

  override val name: String
    get() = "Annotations"
  override val key: SettingsKey<Settings>
    get() = ourKey
  override val previewText: String?
    get() = """
      class Demo {
        private static int pure(int x, int y) {
          return x * y + 10;
        }
      }
    """.trimIndent()

  override fun createConfigurable(settings: Settings): ImmediateConfigurable {
    return object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent {
        return panel {
          row {
            checkBox(ApplicationBundle.message("editor.appearance.show.external.annotations"), settings::showExternal)
          }
          row {
            checkBox(ApplicationBundle.message("editor.appearance.show.external.annotations"), settings::showExternal)
          }
        }
      }
    }
  }

  companion object {
    val ourKey: SettingsKey<Settings> = SettingsKey("annotation.hints")
  }

  data class Settings(var showInferred: Boolean = true, var showExternal: Boolean = true)


  class ToggleSettingsAction(val text: String, val prop: KMutableProperty0<Boolean>) : AnAction() {

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
      prop.set(!prop.get())
    }

  }
}

class InsertAnnotationAction(
  private val project: Project,
  private val file: PsiFile,
  private val element: PsiModifierListOwner
) : AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = "Insert annotation"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val intention = MakeInferredAnnotationExplicit()
    if (intention.isAvailable(project, file, element)) {
      intention.makeAnnotationsExplicit(project, file, element)
    }
  }
}