// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

class PsiTargetNavigator<T: PsiElement>(val supplier: Supplier<List<T>>) {

  constructor(elements: Array<T>) : this(Supplier { elements.toList() })

  private var selection: PsiElement? = null

  fun selection(selection: PsiElement?): PsiTargetNavigator<T> = apply { this.selection = selection }

  fun createPopup(project: Project, @PopupTitle title: String?): JBPopup {
    return createPopup(project, title) { element -> EditSourceUtil.navigateToPsiElement(element) }
  }

  fun createPopup(project: Project, @PopupTitle title: String?, processor: PsiElementProcessor<T>): JBPopup {

    var selected: ItemWithPresentation? = null
    val targets: List<ItemWithPresentation> = ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"), Computable {
      val elements = supplier.get()
      val list = elements.map { ItemWithPresentation(it) }
      selected = if (selection == null) null else list[elements.indexOf(selection)]
      list
    })

    val builder = buildTargetPopupWithMultiSelect(targets, Function { it.presentation }, Predicate {
      @Suppress("UNCHECKED_CAST") ((it.dereference() as T).let { element -> processor.execute(element) })
    })
    title?.let { builder.setTitle(title) }
    selected.let { builder.setSelectedValue(selected, true) }

    return builder.createPopup()
  }
}