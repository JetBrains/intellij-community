// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_CURRENT
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiParameter
import com.intellij.refactoring.rename.inplace.VirtualTemplateElement
import com.intellij.refactoring.rename.inplace.VirtualTemplateElement.Companion.installOnTemplate
import java.awt.Color
import java.util.function.Consumer

fun onClickCallback(psiParameter: PsiParameter): () -> Unit {
  return {
    psiParameter.navigate(true)
  }
}

fun createDelegatePresentation(
  templateState: TemplateState,
  title: @NlsContexts.Button String,
  selectionListener: Consumer<Boolean>,
): InlayPresentation {
  val editor = templateState.editor
  val factory = PresentationFactory(editor as EditorImpl)

  val textPresentation = WithAttributesPresentation(factory.inset(factory.text(title), 4, 0, 6, 3), INLINE_PARAMETER_HINT_CURRENT, editor,
                                                    WithAttributesPresentation.AttributesFlags().withIsDefault(true))
  val colorsScheme = editor.colorsScheme
  fun withBackground(color: Color?) = 
    factory.container(textPresentation,
                      roundedCorners = InlayPresentationFactory.RoundedCorners(3, 3),
                      background = color)

  val biStatePresentation = BiStatePresentation({ withBackground(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_DEFAULT)) },
                                                { withBackground(colorsScheme.getAttributes(INLINE_PARAMETER_HINT_CURRENT).backgroundColor) },
                                                true)

  fun onSelect() {
    biStatePresentation.flipState()
    val delegate = !biStatePresentation.state.currentFirst
    selectionListener.accept(delegate)
  }

  val templateElement: VirtualTemplateElement = object : VirtualTemplateElement {
    override fun onSelect(templateState: TemplateState) {
      onSelect()
    }
  }
  installOnTemplate(templateState, templateElement)

  return factory.onClick(factory.withTooltip(JavaBundle.message("introduce.parameter.inlay.tooltip.delegate"), biStatePresentation),
                         MouseButton.Left) { _, _ ->
    onSelect()
  }
}