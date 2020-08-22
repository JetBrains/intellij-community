// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Experimental

package com.intellij.navigation

import com.intellij.ide.ui.createTargetPresentationRenderer
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.PopupTitle
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Consumer

fun <T> chooseTargetPopup(@PopupTitle title: String,
                          targets: List<T>,
                          presentation: (T) -> TargetPopupPresentation?,
                          consumer: Consumer<in T>): JBPopup {
  return chooseTargetPopup(title, targets, presentation, consumer::accept)
}

fun <T> chooseTargetPopup(@PopupTitle title: String,
                          targets: List<T>,
                          presentation: (T) -> TargetPopupPresentation?,
                          consumer: (T) -> Unit): JBPopup {
  val renderer = createTargetPresentationRenderer(presentation)
  return JBPopupFactory.getInstance()
    .createPopupChooserBuilder(targets)
    .setRenderer(renderer)
    .setNamerForFiltering(renderer::getItemSearchString)
    .setFont(EditorUtil.getEditorFont())
    .setTitle(title)
    .setItemChosenCallback(com.intellij.util.Consumer(consumer))
    .withHintUpdateSupply()
    .createPopup()
}
