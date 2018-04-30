// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner

class AnnotationHintsPass(
  rootElement: PsiElement, editor: Editor,
  modificationStampHolder: ModificationStampHolder
) : ElementProcessingHintPass(rootElement, editor, modificationStampHolder) {

  override fun isAvailable(virtualFile: VirtualFile): Boolean =
    (CodeInsightSettings.getInstance().SHOW_EXTERNAL_ANNOTATIONS_INLINE
     && ExternalAnnotationsManager.getInstance(myProject).hasAnnotationRootsForFile(virtualFile))
    || CodeInsightSettings.getInstance().SHOW_INFERRED_ANNOTATIONS_INLINE

  override fun collectElementHints(element: PsiElement, collector: (offset: Int, hint: String) -> Unit) {
    if (element is PsiModifierListOwner) {
      var annotations = emptySequence<PsiAnnotation>()
      if (CodeInsightSettings.getInstance().SHOW_EXTERNAL_ANNOTATIONS_INLINE) {
        annotations += ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotations(element).orEmpty()
      }
      if (CodeInsightSettings.getInstance().SHOW_INFERRED_ANNOTATIONS_INLINE) {
        annotations += InferredAnnotationsManager.getInstance(myProject).findInferredAnnotations(element)
      }

      val shownAnnotations = mutableSetOf<String>()
      annotations.forEach {
        val nameReferenceElement = it.nameReferenceElement
        if (nameReferenceElement != null && element.modifierList != null &&
            (shownAnnotations.add(nameReferenceElement.qualifiedName) || JavaDocInfoGenerator.isRepeatableAnnotationType(it))) {
          val offset = element.modifierList!!.textRange.startOffset
          collector.invoke(offset, "@" + nameReferenceElement.referenceName + it.parameterList.text)
        }
      }
    }
  }

  override fun getHintKey(): Key<Boolean> = ANNOTATION_INLAY_KEY
  override fun createRenderer(text: String): HintRenderer = AnnotationHintRenderer(text)

  companion object {
    private val ANNOTATION_INLAY_KEY = Key.create<Boolean>("ANNOTATION_INLAY_KEY")
  }

  private class AnnotationHintRenderer(text: String) : HintRenderer(text) {
    override fun getContextMenuGroupId() = "AnnotationHintsContextMenu"
  }

  class ToggleExternalAnnotationsHintsAction : ToggleAction() {
    override fun isSelected(e: AnActionEvent?): Boolean = CodeInsightSettings.getInstance().SHOW_EXTERNAL_ANNOTATIONS_INLINE

    override fun setSelected(e: AnActionEvent?, state: Boolean) {
      CodeInsightSettings.getInstance().SHOW_EXTERNAL_ANNOTATIONS_INLINE = state
      AnnotationHintsPassFactory.modificationStampHolder.forceHintsUpdateOnNextPass()
    }
  }

  class ToggleInferredAnnotationsHintsAction : ToggleAction() {
    override fun isSelected(e: AnActionEvent?): Boolean = CodeInsightSettings.getInstance().SHOW_INFERRED_ANNOTATIONS_INLINE

    override fun setSelected(e: AnActionEvent?, state: Boolean) {
      CodeInsightSettings.getInstance().SHOW_INFERRED_ANNOTATIONS_INLINE = state
      AnnotationHintsPassFactory.modificationStampHolder.forceHintsUpdateOnNextPass()
    }
  }
}