// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.error

import com.intellij.collaboration.ui.util.getName
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.KeyStroke
import javax.swing.event.HyperlinkEvent

object ErrorStatusPanelFactory {
  private const val ERROR_ACTION_HREF = "ERROR_ACTION"

  fun <T> create(
    scope: CoroutineScope,
    errorState: StateFlow<T?>,
    errorPresenter: ErrorStatusPresenter<T>
  ): JComponent {
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

  private class Controller<T>(
    scope: CoroutineScope,
    errorState: StateFlow<T?>,
    private val errorPresenter: ErrorStatusPresenter<T>,
    private val htmlEditorPane: JEditorPane
  ) {
    private var action: Action? = null

    init {
      htmlEditorPane.apply {
        addHyperlinkListener(object : HyperlinkAdapter() {
          override fun hyperlinkActivated(event: HyperlinkEvent) {
            if (event.description == ERROR_ACTION_HREF) {
              val actionEvent = ActionEvent(htmlEditorPane, ActionEvent.ACTION_PERFORMED, "perform")
              action?.actionPerformed(actionEvent)
            }
            else {
              BrowserUtil.browse(event.description)
            }
          }
        })
        registerKeyboardAction(
          ActionListener { action?.actionPerformed(it) },
          KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
          JComponent.WHEN_FOCUSED
        )
      }

      scope.launch {
        errorState.collect { error ->
          update(error)
        }
      }
    }

    private fun update(error: T?) {
      if (error == null) {
        htmlEditorPane.text = ""
        htmlEditorPane.isVisible = false
        return
      }

      val errorTextBuilder = HtmlBuilder().apply {
        appendP(errorPresenter.getErrorTitle(error))
        val errorTitle = errorPresenter.getErrorDescription(error)
        if (errorTitle != null) {
          appendP(errorTitle)
        }
      }

      val errorAction = errorPresenter.getErrorAction(error)
      if (errorAction != null) {
        action = errorAction
        errorTextBuilder.appendP(HtmlChunk.link(ERROR_ACTION_HREF, errorAction.getName()))
      }

      htmlEditorPane.text = errorTextBuilder.wrapWithHtmlBody().toString()
      htmlEditorPane.isVisible = true
    }

    private fun HtmlBuilder.appendP(chunk: HtmlChunk): HtmlBuilder = append(HtmlChunk.p().attr("align", "center").child(chunk))

    private fun HtmlBuilder.appendP(@Nls text: String): HtmlBuilder = appendP(HtmlChunk.text(text))
  }
}