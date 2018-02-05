// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.SyntaxTraverser
import gnu.trove.TIntObjectHashMap

class AnnotationHintsPass(private val rootElement: PsiElement, editor: Editor) : EditorBoundHighlightingPass(editor,
                                                                                                             rootElement.containingFile,
                                                                                                             true) {
  private val hints = TIntObjectHashMap<MutableList<HintData>>()
  private val traverser: SyntaxTraverser<PsiElement> = SyntaxTraverser.psiTraverser(rootElement)

  override fun doCollectInformation(progress: ProgressIndicator) {
    assert(myDocument != null)
    hints.clear()

    if (showAnnotations()) {
      traverser.forEach { process(it) }
    }
  }

  private fun showAnnotations(): Boolean {
    val value = Registry.get("java.annotations.show.inline")
    if (value.isBoolean) return value.asBoolean()
    return "internal" == value.asString() && ApplicationManager.getApplication().isInternal
  }

  private fun process(element: PsiElement) {
    if (element is PsiModifierListOwner && showAnnotations()) {
      val externalAnnotations = ExternalAnnotationsManager.getInstance(element.project).findExternalAnnotations(element)
      val inferredAnnotations = InferredAnnotationsManager.getInstance(element.project).findInferredAnnotations(element)

      (externalAnnotations.orEmpty().asSequence() + inferredAnnotations.asSequence())
        .forEach {
          if (it.nameReferenceElement != null && element.modifierList != null) {
            val offset = element.modifierList!!.textRange.startOffset
            var hintList = hints.get(offset)
            if (hintList == null) {
              hintList = arrayListOf()
              hints.put(offset, hintList)
            }
            hintList.add(HintData("@" + it.nameReferenceElement?.referenceName + it.parameterList.text))
          }
        }
    }
  }

  override fun doApplyInformationToEditor() {
    val keeper = CaretVisualPositionKeeper(myEditor)

    val inlayModel = myEditor.inlayModel
    inlayModel.getInlineElementsInRange(rootElement.textRange.startOffset + 1, rootElement.textRange.endOffset - 1)
      .filter { ANNOTATION_INLAY_KEY.isIn(it) }
      .forEach { Disposer.dispose(it) }

    hints.forEachEntry { offset, info ->
      info.forEach {
        val inlay = inlayModel.addInlineElement(offset, HintRenderer(it.presentationText))
        inlay.putUserData(ANNOTATION_INLAY_KEY, true)
      }
      true
    }

    keeper.restoreOriginalLocation(false)
  }

  class HintData(val presentationText: String)

  companion object {
    private val ANNOTATION_INLAY_KEY = Key.create<Boolean>("ANNOTATION_INLAY_KEY")
  }
}