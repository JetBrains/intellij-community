// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.list.createTargetPresentationRenderer

class PsiTargetNavigator {

  fun <T: PsiElement> createPopup(elements: Array<T>, @PopupTitle title: String?): JBPopup  {
    return createPopup(elements, title) { element -> EditSourceUtil.navigateToPsiElement(element) }
  }

  fun <T: PsiElement> createPopup(elements: Array<T>, @PopupTitle title: String?, processor: PsiElementProcessor<T>): JBPopup {

    val project = elements[0].project
    val targets: List<ItemWithPresentation> = GotoTargetHandler.computePresentationInBackground(project, elements, false)

    val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(targets)
      .setRenderer(createTargetPresentationRenderer { it.presentation })
      .setNamerForFiltering { it.presentation.presentableText }
      .setFont(EditorUtil.getEditorFont())
      .withHintUpdateSupply()
      .setItemsChosenCallback { items ->
        items.forEach { it ->
          @Suppress("UNCHECKED_CAST")
          (it.dereference() as? T)?.let { processor.execute(it) }
        }
      }
    if (title != null) {
      builder.setTitle(title)
    }
    if (builder is PopupChooserBuilder<*>) {
      val pane = (builder as PopupChooserBuilder<*>).scrollPane
      pane?.border = null
      pane?.viewportBorder = null
    }

    return builder.createPopup()
  }
}