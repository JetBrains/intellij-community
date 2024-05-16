// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.error

import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.collaboration.ui.util.name
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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

  @JvmOverloads
  fun <T> create(
    scope: CoroutineScope,
    errorState: Flow<T?>,
    errorPresenter: ErrorStatusPresenter<T>,
    alignment: Alignment = Alignment.CENTER
  ): JComponent {
    val htmlEditorPane = JEditorPane().apply {
      editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
      foreground = NamedColorUtil.getErrorForeground()
      isFocusable = true
      isEditable = false
      isOpaque = false
    }

    var action: Action? = null
    htmlEditorPane.addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(event: HyperlinkEvent) {
        if (event.description == ErrorStatusPresenter.ERROR_ACTION_HREF) {
          val actionEvent = ActionEvent(htmlEditorPane, ActionEvent.ACTION_PERFORMED, "perform")
          action?.actionPerformed(actionEvent)
        }
        else {
          BrowserUtil.browse(event.description)
        }
      }
    })
    htmlEditorPane.registerKeyboardAction(
      ActionListener { action?.actionPerformed(it) },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      JComponent.WHEN_FOCUSED
    )

    scope.launch {
      errorState.collect { error ->
        htmlEditorPane.update(alignment, error, errorPresenter) { action = it }
      }
    }

    return htmlEditorPane
  }

  private fun <T> JEditorPane.update(alignment: Alignment, error: T?, errorPresenter: ErrorStatusPresenter<T>, setAction: (Action?) -> Unit) {
    if (error == null) {
      setHtmlBody("")
      isVisible = false
      return
    }

    val errorAction = errorPresenter.getErrorAction(error)
    setAction(errorAction)

    setHtmlBody(
      when (errorPresenter) {
        is ErrorStatusPresenter.HTML -> errorPresenter.getHTMLBody(error)
        is ErrorStatusPresenter.Text -> getErrorText(alignment, errorAction, error, errorPresenter)
      })
    isVisible = true
  }

  private fun <T> getErrorText(alignment: Alignment, errorAction: Action?, error: T, errorPresenter: ErrorStatusPresenter.Text<T>): String {
    val errorTextBuilder = HtmlBuilder().apply {
      appendP(alignment, errorPresenter.getErrorTitle(error))
      val errorTitle = errorPresenter.getErrorDescription(error)
      if (errorTitle != null) {
        appendP(alignment, errorTitle)
      }
    }

    if (errorAction != null) {
      errorTextBuilder.appendP(alignment, HtmlChunk.link(ErrorStatusPresenter.ERROR_ACTION_HREF, errorAction.name.orEmpty()))
    }

    return errorTextBuilder.wrapWithHtmlBody().toString()
  }

  private fun HtmlBuilder.appendP(alignment: Alignment, chunk: HtmlChunk): HtmlBuilder =
    append(HtmlChunk.p().attr("align", alignment.htmlValue).child(chunk))

  private fun HtmlBuilder.appendP(alignment: Alignment, @Nls text: String): HtmlBuilder =
    appendP(alignment, HtmlChunk.text(text))

  enum class Alignment(val htmlValue: String) {
    LEFT("left"),
    CENTER("center");
  }
}