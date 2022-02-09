// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.MakeInferredAnnotationExplicit
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.*
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

class AnnotationInlayProvider : InlayHintsProvider<AnnotationInlayProvider.Settings> {

  override val group: InlayGroup
    get() = InlayGroup.ANNOTATIONS_GROUP

  override fun getCollectorFor(file: PsiFile,
                               editor: Editor,
                               settings: Settings,
                               sink: InlayHintsSink): InlayHintsCollector? {
    val project = file.project
    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    return object : FactoryInlayHintsCollector(editor) {
      override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (file.project.service<DumbService>().isDumb) return false
        if (file.project.isDefault) return false
        val presentations = SmartList<InlayPresentation>()
        if (element is PsiModifierListOwner) {
          var annotations = emptySequence<PsiAnnotation>()
          if (settings.showExternal) {
            annotations += ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(element).orEmpty()
          }
          if (settings.showInferred) {
            annotations += InferredAnnotationsManager.getInstance(project).findInferredAnnotations(element)
          }
          val previewAnnotation = PREVIEW_ANNOTATION_KEY.get(element)
          if (previewAnnotation != null) {
            annotations += previewAnnotation
          }

          val shownAnnotations = mutableSetOf<String>()
          annotations.forEach {
            val nameReferenceElement = it.nameReferenceElement
            if (nameReferenceElement != null && element.modifierList != null &&
                (shownAnnotations.add(nameReferenceElement.qualifiedName) || JavaDocInfoGenerator.isRepeatableAnnotationType(it))) {
              presentations.add(createPresentation(it, element))
            }
          }
          val modifierList = element.modifierList
          if (modifierList != null) {
            val textRange = modifierList.textRange ?: return true
            val offset = textRange.startOffset
            if (presentations.isNotEmpty()) {
              val presentation = SequencePresentation(presentations)
              val prevSibling = element.prevSibling
              when {
                // element is first in line
                prevSibling is PsiWhiteSpace && element !is PsiParameter && prevSibling.textContains('\n') && document != null -> {
                  val width = EditorUtil.getPlainSpaceWidth(editor)
                  val line = document.getLineNumber(offset)
                  val startOffset = document.getLineStartOffset(line)
                  val column = offset - startOffset
                  val shifted = factory.inset(presentation, left = column * width)

                  sink.addBlockElement(offset, true, true, BlockInlayPriority.ANNOTATIONS, shifted)
                }
                else -> {
                  sink.addInlineElement(offset, false, factory.inset(presentation, left = 1, right = 1), false)
                }
              }
            }
          }
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
            ToggleSettingsAction(JavaBundle.message("settings.inlay.java.turn.off.external.annotations"), settings::showExternal, settings),
            ToggleSettingsAction(JavaBundle.message("settings.inlay.java.turn.off.inferred.annotations"), settings::showInferred, settings)
          )
        }
      }

      private fun annotationPresentation(annotation: PsiAnnotation): InlayPresentation = with(factory) {
        val nameReferenceElement = annotation.nameReferenceElement
        val parameterList = annotation.parameterList

        val presentations = mutableListOf(
          smallText("@"),
          psiSingleReference(smallText(nameReferenceElement?.referenceName ?: "")) { nameReferenceElement?.resolve() }
        )

        parametersPresentation(parameterList)?.let {
          presentations.add(it)
        }
        roundWithBackground(SequencePresentation(presentations))
      }

      private fun parametersPresentation(parameterList: PsiAnnotationParameterList) = with(factory) {
        val attributes = parameterList.attributes
        when {
          attributes.isEmpty() -> null
          else -> insideParametersPresentation(attributes, collapsed = parameterList.textLength > 60)
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
        when (val attrName = attribute.name) {
          null -> attrValuePresentation(attribute)
          else -> seq(
            psiSingleReference(smallText(attrName), resolve = { attribute.reference?.resolve() }),
            smallText(" = "),
            attrValuePresentation(attribute)
          )
        }
      }

      private fun PresentationFactory.attrValuePresentation(attribute: PsiNameValuePair) =
        smallText(attribute.value?.text ?: "")
    }
  }

  override fun createSettings(): Settings = Settings()

  override val name: String
    get() = JavaBundle.message("settings.inlay.java.annotations")
  override val key: SettingsKey<Settings>
    get() = ourKey

  override fun getProperty(key: String): String {
    return JavaBundle.message(key)
  }

  override val previewText: String? = null

  private val PREVIEW_ANNOTATION_KEY = Key.create<PsiAnnotation>("preview.annotation.key")

  override fun preparePreview(editor: Editor, file: PsiFile, settings: Settings) {
    val psiMethod = (file as PsiJavaFile).classes[0].methods[0]
    val factory = PsiElementFactory.getInstance(file.project)
    if (psiMethod.parameterList.isEmpty) {
      if (settings.showExternal) {
        PREVIEW_ANNOTATION_KEY.set(psiMethod, factory.createAnnotationFromText("@Deprecated", psiMethod))
      }
    }
    else if (settings.showInferred)
      PREVIEW_ANNOTATION_KEY.set(psiMethod.parameterList.getParameter(0), factory.createAnnotationFromText("@NotNull", psiMethod))
  }

  override fun createConfigurable(settings: Settings): ImmediateConfigurable {
    return object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent = panel {}

      override val mainCheckboxText: String
        get() = JavaBundle.message("settings.inlay.java.show.hints.for")

      override val cases: List<ImmediateConfigurable.Case>
        get() = listOf(
          ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.inferred.annotations"), "inferred.annotations", settings::showInferred),
          ImmediateConfigurable.Case(JavaBundle.message("settings.inlay.java.external.annotations"), "external.annotations", settings::showExternal)
        )
    }
  }

  companion object {
    val ourKey: SettingsKey<Settings> = SettingsKey("annotation.hints")
  }

  data class Settings(var showInferred: Boolean = false, var showExternal: Boolean = true)


  class ToggleSettingsAction(@NlsActions.ActionText val text: String, val prop: KMutableProperty0<Boolean>, val settings: Settings) : AnAction() {

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
      prop.set(!prop.get())
      val storage = InlayHintsSettings.instance()
      storage.storeSettings(ourKey, JavaLanguage.INSTANCE, settings)
      InlayHintsPassFactory.forceHintsUpdateOnNextPass()
    }

  }
}

class InsertAnnotationAction(
  private val project: Project,
  private val file: PsiFile,
  private val element: PsiModifierListOwner
) : AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = JavaBundle.message("settings.inlay.java.insert.annotation")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val intention = MakeInferredAnnotationExplicit()
    if (intention.isAvailable(file, element)) {
      intention.makeAnnotationsExplicit(project, file, element)
    }
  }
}
