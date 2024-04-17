// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.codeInsight.navigation.collectRelatedItems
import com.intellij.codeInsight.navigation.getRelatedItemsPopup
import com.intellij.lang.LangBundle
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly

/**
 * @author Dmitry Avdeev
 */
class GotoRelatedSymbolAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val element = getContextElement(e.dataContext)
    e.presentation.isEnabled = element != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext

    val element = getContextElement(dataContext) ?: return

    // it's calculated in advance because `NavigationUtil.collectRelatedItems` might be
    // calculated under a cancellable progress, and we can't use the data context anymore,
    // since it can't be reused between swing events
    val popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext)

    val items = collectRelatedItems(element, dataContext)
    if (items.isEmpty()) {
      val component: Any? = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext)
      if (component is EditorComponentImpl) {
        val point = popupLocation.point
        point.translate(0, -component.editor.lineHeight)
      }

      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(LangBundle.message("hint.text.no.related.symbols"), MessageType.ERROR, null)
        .setFadeoutTime(3000)
        .createBalloon()
        .show(popupLocation, Balloon.Position.above)
      return
    }

    if (items.size == 1) {
      items[0].navigate()
      return
    }
    getRelatedItemsPopup(items, LangBundle.message("popup.title.choose.target")).show(popupLocation)
  }

  companion object {
    @TestOnly
    @JvmStatic
    fun getItems(psiFile: PsiFile, editor: Editor?, dataContext: DataContext?): List<GotoRelatedItem> {
      return collectRelatedItems(getContextElement(psiFile, editor), dataContext)
    }

    private fun getContextElement(dataContext: DataContext): PsiElement? {
      val file = CommonDataKeys.PSI_FILE.getData(dataContext)
      val editor = CommonDataKeys.EDITOR.getData(dataContext)
      val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
      if (file != null && editor != null) {
        return getContextElement(file, editor)
      }
      return element ?: file
    }

    private fun getContextElement(psiFile: PsiFile, editor: Editor?): PsiElement {
      var contextElement: PsiElement = psiFile
      if (editor != null) {
        val element = psiFile.findElementAt(editor.caretModel.offset)
        if (element != null) {
          contextElement = element
        }
      }
      return contextElement
    }
  }
}
