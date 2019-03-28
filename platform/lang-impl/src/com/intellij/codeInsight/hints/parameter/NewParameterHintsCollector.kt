// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.parameter

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayModelWrapper
import com.intellij.codeInsight.hints.ParameterHintsUpdater
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.SmartList
import gnu.trove.TIntObjectHashMap

internal class NewParameterHintsCollector<T: Any>(
  override val key: SettingsKey<ParameterHintsSettings<T>>,
  val provider: NewParameterHintsProvider<T>,
  val settings: ParameterHintsSettings<T>,
  val editor: Editor,
  val myRootElement: PsiFile,
  private val myForceImmediateUpdate: Boolean
) : InlayHintsCollector<ParameterHintsSettings<T>> {
  private val myFilter = ParameterBlackListFilter(settings.blackList)
  private val myTraverser: SyntaxTraverser<PsiElement> = SyntaxTraverser.psiTraverser(myRootElement)
  private val myHints = TIntObjectHashMap<MutableList<ParameterHintInfo>>()
  private val myShowOnlyIfExistedBeforeHints = TIntObjectHashMap<InlayPresentation>()

  private val mySink = object: ParameterHintsSink {

    override fun addHint(info: ParameterHintInfo) {
      val offset = info.offset
      if (!canShowHintsAtOffset(offset, editor.document, myRootElement)) return
      val blackListInfo = info.blackListInfo
      if (blackListInfo != null && !myFilter.shouldShowHint(blackListInfo)) return
      // TODO consider HintWidthAdjustment
      // But how? Isn't it should be handled inside presentation?
      if (info.isShowOnlyIfExistedBefore) {
        myShowOnlyIfExistedBeforeHints.put(offset, info.presentation)
      }
      else {
        var hintList: MutableList<ParameterHintInfo>? = myHints.get(offset)
        if (hintList == null) {
          hintList = SmartList<ParameterHintInfo>()
          myHints.put(offset, hintList)
        }
        hintList.add(info)
      }
    }
  }

  override fun collect(element: PsiElement, editor: Editor, settings: ParameterHintsSettings<T>, isEnabled: Boolean) {
    val factory = PresentationFactory(editor as EditorImpl)
    myTraverser.forEach {
      provider.getParameterHints(it, settings.providerSettings, factory, mySink)
    }
  }

  override fun apply(element: PsiElement, editor: Editor, model: InlayModelWrapper, settings: ParameterHintsSettings<T>) {
    val keeper = CaretVisualPositionKeeper(editor)
    keeper.restoreOriginalLocation(false)
    val manager = ParameterHintsPresentationManager.getInstance()
    val hints = hintsInRootElementArea(manager, editor)
    val updater = NewParameterHintsUpdater(editor, hints, myHints, myShowOnlyIfExistedBeforeHints, myForceImmediateUpdate, model)
    updater.update()


  }

  private fun hintsInRootElementArea(manager: ParameterHintsPresentationManager, editor: Editor): List<Inlay<*>> {
    val document = editor.document
    val range = myRootElement.textRange
    var elementStart = range.startOffset
    var elementEnd = range.endOffset

    // Adding hints on the borders is allowed only in case root element is a document
    // See: canShowHintsAtOffset
    if (document.textLength != range.length) {
      ++elementStart
      --elementEnd
    }

    return manager.getParameterHintsInRange(editor, elementStart, elementEnd)
  }



  /**
   * Adding hints on the borders of root element (at startOffset or endOffset)
   * is allowed only in the case when root element is a document
   *
   * @return true if a given offset can be used for hint rendering
   */
  private fun canShowHintsAtOffset(offset: Int, document: Document, rootElement: PsiElement): Boolean {
    val rootRange = rootElement.textRange
    if (!rootRange.containsOffset(offset)) return false
    return if (offset > rootRange.startOffset && offset < rootRange.endOffset) true else document.textLength == rootRange.length
  }

}