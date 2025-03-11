// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.impl.InlayProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiClass
import java.time.LocalDate

class JavaExceptionFilter : JvmExceptionOccurrenceFilter {
  override fun applyFilter(
    exceptionClassName: String,
    classes: MutableList<PsiClass?>,
    exceptionStartOffset: Int,
  ): ResultItem? {
    with(Registry.get("java.exceptions.anniversary.banner")) {
      if (isOptionEnabled("off")) {
        return null
      }
      if (isOptionEnabled("on")) {
        val startDate = LocalDate.of(2025, 5, 23)
        val endDate = LocalDate.of(2025, 5, 30)
        if (LocalDate.now() !in startDate..endDate) {
          return null
        }
      }
    }
    return CreateExceptionBreakpointResult(exceptionStartOffset, exceptionStartOffset + exceptionClassName.length)
  }

  private class CreateExceptionBreakpointResult(highlightStartOffset: Int, highlightEndOffset: Int) : ResultItem(highlightStartOffset, highlightEndOffset, null), InlayProvider {
    override fun createInlay(editor: Editor, offset: Int): Inlay<*>? {
      return editor.getInlayModel().addBlockElement<EditorCustomElementRenderer?>(offset, true, false, 0, createInlayRenderer(editor))
    }

    override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
      val presentation = with(PresentationFactory(editor)) {
        roundWithBackground(
          seq(
            smallScaledIcon(AllIcons.Promo.JavaDuke),
          inset(
              smallText("from Java with love - happy 30th anniversary! (captured by IntelliJ IDEA)"),
              left = 6, top = 1, down = 1
            )
          )
        )
      }
      return PresentationRenderer(presentation)
    }
  }
}
