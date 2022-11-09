// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.error

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JEditorPane

object ErrorStatusPanelFactory {
  fun create(scope: CoroutineScope, errorState: StateFlow<Throwable?>, errorPresenter: ErrorStatusPresenter): JComponent {
    val htmlEditorPane = JEditorPane().apply {
      editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
      foreground = NamedColorUtil.getErrorForeground()
      isFocusable = true
      isEditable = false
      isOpaque = false
    }

    Controller(scope, errorState, errorPresenter, htmlEditorPane)

    return htmlEditorPane
  }

  private class Controller(
    scope: CoroutineScope,
    errorState: StateFlow<Throwable?>,
    private val errorPresenter: ErrorStatusPresenter,
    private val htmlEditorPane: JEditorPane
  ) {
    init {
      scope.launch {
        errorState.collect { error ->
          update(error)
        }
      }
    }

    private fun update(error: Throwable?) {
      if (error == null) {
        htmlEditorPane.text = ""
        htmlEditorPane.isVisible = false
        return
      }

      val errorTitle = errorPresenter.getErrorTitle(error)
      val errorDescription = errorPresenter.getErrorDescription(error)
      val errorTextBuilder = HtmlBuilder()
        .appendP(errorTitle)
        .appendP(errorDescription)

      htmlEditorPane.text = errorTextBuilder.wrapWithHtmlBody().toString()
      htmlEditorPane.isVisible = true
    }

    private fun HtmlBuilder.appendP(chunk: HtmlChunk): HtmlBuilder = append(HtmlChunk.p().attr("align", "center").child(chunk))

    private fun HtmlBuilder.appendP(@Nls text: String): HtmlBuilder = appendP(HtmlChunk.text(text))
  }
}