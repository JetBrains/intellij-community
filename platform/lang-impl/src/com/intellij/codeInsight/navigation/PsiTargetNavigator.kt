// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import java.util.function.Function
import java.util.function.Predicate

class PsiTargetNavigator {

  fun <T: PsiElement> createPopup(elements: Array<T>, @PopupTitle title: String?): JBPopup  {
    return createPopup(elements, title) { element -> EditSourceUtil.navigateToPsiElement(element) }
  }

  fun <T: PsiElement> createPopup(elements: Array<T>, @PopupTitle title: String?, processor: PsiElementProcessor<T>): JBPopup {

    val project = elements[0].project
    val targets: List<ItemWithPresentation> = GotoTargetHandler.computePresentationInBackground(project, elements, false)

    val builder = buildTargetPopupWithMultiSelect(targets, Function { it.presentation }, Predicate {
      @Suppress("UNCHECKED_CAST") ((it.dereference() as T).let { element -> processor.execute(element) })
    })
    if (title != null) {
      builder.setTitle(title)
    }

    return builder.createPopup()
  }
}