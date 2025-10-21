// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.MakeInferredAnnotationExplicit
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.codeInspection.dataFlow.Mutability
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.annotations.PropertyKey

private const val LIST_OWNER_ELEMENT = "list.owner.element"
private const val TOGGLES_TO_SHOW = "toggles.to.show"
private const val SHOW_EXTERNAL_TOGGLE_TAG = "e"
private const val SHOW_EXTERNAL_AND_INTERNAL_TOGGLES_TAG = "b"
private val EXTERNAL_AND_INFERRED_TOGGLES_PAYLOAD =
  InlayPayload(TOGGLES_TO_SHOW, StringInlayActionPayload(SHOW_EXTERNAL_AND_INTERNAL_TOGGLES_TAG))
private val TYPE_ANNOTATION_PAYLOADS = listOf(InlayPayload(TOGGLES_TO_SHOW, StringInlayActionPayload(SHOW_EXTERNAL_TOGGLE_TAG)))

private val ARRAY_TYPE_START = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.ELLIPSIS)

private val HINT_FORMAT = HintFormat(
  HintColorKind.Default,
  HintFontSize.ABitSmallerThanInEditor,
  HintMarginPadding.OnlyPadding,
)

public class AnnotationInlayProvider : InlayHintsProvider {
  public companion object {
    public const val PROVIDER_ID: String = "java.annotation.hints"
    public const val SHOW_INFERRED: String = "showInferred"
    public const val SHOW_EXTERNAL: String = "showExternal"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    val project = file.project
    if (project.isDefault) return null
    return object : SharedBypassCollector {
      override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        if (element is PsiTypeElement) {
          sink.whenOptionEnabled(SHOW_EXTERNAL) {
            val originalElement = element.originalElement
            if (originalElement is PsiTypeElement && originalElement is PsiCompiledElement) {
              val type = originalElement.type
              val offset = element.textRange.startOffset
              val manager = ExternalAnnotationsManager.getInstance(project)
              type.annotations
                .filter(manager::isExternalAnnotation)
                .forEach {
                  sink.addAnnotationPresentation(it, project, InlineInlayPosition(offset, false), HINT_FORMAT, TYPE_ANNOTATION_PAYLOADS)
                }
            }
          }
        }
        else if (element is PsiModifierListOwner) {
          val typeHintPos by lazy(LazyThreadSafetyMode.NONE) { calcTypeHintPosition(element) }
          val modifierListHintPos by lazy(LazyThreadSafetyMode.NONE) { calcModifierListHintPosition(element) }
          val inlayPayloads = listOf(
            InlayPayload(LIST_OWNER_ELEMENT, PsiPointerInlayActionPayload(element.createSmartPointer())),
            EXTERNAL_AND_INFERRED_TOGGLES_PAYLOAD
          )
          val shownAnnotations = mutableSetOf<String>()

          fun InlayTreeSink.addAnnotationIfNotDuplicated(annotation: PsiAnnotation) {
            val nameReferenceElement = annotation.nameReferenceElement
            if (nameReferenceElement != null &&
                element.modifierList != null &&
                (shownAnnotations.add(nameReferenceElement.qualifiedName) || JavaDocInfoGenerator.isRepeatableAnnotationType(annotation))) {
              val hintPos = (if (isTypeAnno(annotation)) typeHintPos else modifierListHintPos) ?: return
              addAnnotationPresentation(annotation, project, hintPos, HINT_FORMAT, inlayPayloads)
            }
          }

          sink.whenOptionEnabled(SHOW_EXTERNAL) {
            ExternalAnnotationsManager.getInstance(project)
              .findExternalAnnotations(element)
              .forEach { sink.addAnnotationIfNotDuplicated(it) }
          }
          sink.whenOptionEnabled(SHOW_INFERRED) {
            InferredAnnotationsManager.getInstance(project)
              .findInferredAnnotations(element)
              .forEach { sink.addAnnotationIfNotDuplicated(it) }
          }
        }
      }
    }
  }
}

private fun InlayTreeSink.addAnnotationPresentation(
  annotation: PsiAnnotation,
  project: Project,
  position: InlayPosition,
  hintFormat: HintFormat,
  payloads: List<InlayPayload>,
) {
  addPresentation(position, hintFormat = hintFormat, payloads = payloads) {
    text("@")
    text(annotation.nameReferenceElement?.referenceName ?: "",
         annotation.nameReferenceElement?.resolve()?.createSmartPointer(project)?.toNavigateInlayAction())
    val parameterList = annotation.parameterList
    val attributes = parameterList.attributes
    if (attributes.isNotEmpty()) {
      collapsibleList(
        state = if (parameterList.textLength > 60) CollapseState.Collapsed else CollapseState.Expanded,
        collapsedState = {
          toggleButton { text("(...)") }
        },
        expandedState = {
          toggleButton { text("(") }
          attributes.joinPresentations(separator = { text(", ") }) { attribute ->
            val name = attribute.name
            if (name != null) {
              text(name, attribute.reference?.resolve()?.createSmartPointer(project)?.toNavigateInlayAction())
              text(" = ")
            }
            text(attribute.value?.text ?: "")
          }
          toggleButton { text(")") }
        }
      )
    }
  }
}

private fun calcModifierListHintPosition(element: PsiModifierListOwner): InlayPosition? {
  val offset = element.modifierList?.textRange?.startOffset ?: return null
  val prevSibling = element.prevSibling
  return when {
    // element is first in line
    prevSibling is PsiWhiteSpace && element !is PsiParameter && prevSibling.textContains('\n') -> {
      AboveLineIndentedPosition(offset, verticalPriority = BlockInlayPriority.ANNOTATIONS)
    }
    else -> {
      InlineInlayPosition(offset, relatedToPrevious = false, priority = 1)
    }
  }
}

private fun calcTypeHintPosition(element: PsiModifierListOwner): InlineInlayPosition? {
  val typeElement = when (element) {
                      is PsiVariable -> element.typeElement
                      is PsiMethod -> element.returnTypeElement
                      else -> null
                    } ?: return null
  val anchor = typeElement.children.firstOrNull { PsiUtil.isJavaToken(it, ARRAY_TYPE_START) } ?: typeElement
  val offset = anchor.textRange.startOffset
  return InlineInlayPosition(offset, relatedToPrevious = false, priority = 0)
}

private fun isTypeAnno(annotation: PsiAnnotation): Boolean {
  val qualifiedName = annotation.qualifiedName ?: return false
  val typeAnno = qualifiedName == AnnotationUtil.NOT_NULL ||
                 qualifiedName == AnnotationUtil.NULLABLE ||
                 qualifiedName == AnnotationUtil.UNKNOWN_NULLABILITY ||
                 qualifiedName == Mutability.UNMODIFIABLE_ANNOTATION ||
                 qualifiedName == Mutability.UNMODIFIABLE_VIEW_ANNOTATION
  return typeAnno && PsiUtil.isAvailable(JavaFeature.TYPE_ANNOTATIONS, annotation)
}

private fun SmartPsiElementPointer<*>.toNavigateInlayAction(): InlayActionData {
  return InlayActionData(PsiPointerInlayActionPayload(this), PsiPointerInlayActionNavigationHandler.HANDLER_ID)
}

private fun <T> Array<T>.joinPresentations(separator: () -> Unit, transform: (T) -> Unit) {
  if (isEmpty()) return
  transform(first())
  for (i in 1..<size) {
    separator()
    transform(get(i))
  }
}

public class InsertAnnotationAction() : AnAction() {
  override fun update(e: AnActionEvent) {
    if (e.hasAnnotationProviderId()) {
      e.presentation.isEnabledAndVisible = e.psiFile?.virtualFile?.isInLocalFileSystem == true
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!e.hasAnnotationProviderId()) return
    val project = e.project ?: return
    val file = e.psiFile ?: return
    val payload = e.inlayPayloads?.get(LIST_OWNER_ELEMENT) as? PsiPointerInlayActionPayload ?: return
    val intention = MakeInferredAnnotationExplicit()
    val element = payload.pointer.element as? PsiModifierListOwner ?: return
    if (intention.isAvailable(file, element)) {
      intention.makeAnnotationsExplicit(project, file, element)
    }
  }
}

public abstract class ToggleAnnotationsOptionAction(
  private val optionId: String,
  private val turnOnKey: @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String,
  private val turnOffKey: @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String,
  private val shouldEnableAndShowToggle: (tag: String) -> Boolean,
) : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (e.editor != null && e.hasAnnotationProviderId() && shouldEnableAndShowToggle(e.togglesToShowTag)) {
      e.presentation.isEnabledAndVisible = true
      val isOptionEnabled = DeclarativeInlayHintsSettings.getInstance()
        .isOptionEnabled(optionId, AnnotationInlayProvider.PROVIDER_ID) == true
      e.presentation.text = JavaBundle.message(
        if (isOptionEnabled) turnOffKey
        else turnOnKey
      )
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor ?: return
    val project = e.project ?: return
    WriteAction.run<Throwable> {
      DeclarativeInlayHintsSettings.getInstance().apply {
        val isOptionEnabled = isOptionEnabled(optionId, AnnotationInlayProvider.PROVIDER_ID) == true
        setOptionEnabled(optionId, AnnotationInlayProvider.PROVIDER_ID, !isOptionEnabled)
      }
    }
    DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
  }
}

public class ToggleInferredAnnotationsAction : ToggleAnnotationsOptionAction(
  optionId = AnnotationInlayProvider.SHOW_INFERRED,
  turnOnKey = "settings.inlay.java.turn.on.showInferred.annotations",
  turnOffKey = "settings.inlay.java.turn.off.showInferred.annotations",
  shouldEnableAndShowToggle = { tag -> tag == SHOW_EXTERNAL_AND_INTERNAL_TOGGLES_TAG }
)

public class ToggleExternalAnnotationsAction : ToggleAnnotationsOptionAction(
  optionId = AnnotationInlayProvider.SHOW_EXTERNAL,
  turnOnKey = "settings.inlay.java.turn.on.showExternal.annotations",
  turnOffKey = "settings.inlay.java.turn.off.showExternal.annotations",
  shouldEnableAndShowToggle = { true }
)

private fun AnActionEvent.hasAnnotationProviderId(): Boolean =
  getData(InlayHintsProvider.PROVIDER_ID) == AnnotationInlayProvider.PROVIDER_ID

private val AnActionEvent.inlayPayloads: Map<String, InlayActionPayload>?
  get() = getData(InlayHintsProvider.INLAY_PAYLOADS)

private val AnActionEvent.togglesToShowTag: String
  get() = inlayPayloads
            ?.get(TOGGLES_TO_SHOW)
            ?.let { (it as? StringInlayActionPayload)?.text }
          ?: error("Annotation inlay hint does not specify option toggles correctly")

private val AnActionEvent.editor: Editor?
  get() = CommonDataKeys.EDITOR.getData(this.dataContext)

private val AnActionEvent.psiFile: PsiFile?
  get() = getData(CommonDataKeys.PSI_FILE)