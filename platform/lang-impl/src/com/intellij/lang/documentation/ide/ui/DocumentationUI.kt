// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.*
import com.intellij.codeInsight.documentation.DocumentationManager.SELECTED_QUICK_DOC_TEXT
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationData
import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.lang.documentation.ide.actions.DOCUMENTATION_BROWSER
import com.intellij.lang.documentation.ide.actions.PRIMARY_GROUP_ID
import com.intellij.lang.documentation.ide.actions.registerBackForwardActions
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.PopupHandler
import com.intellij.util.SmartList
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

internal class DocumentationUI(
  project: Project,
  val browser: DocumentationBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane

  private val htmlFactory: DocumentationHtmlFactory get() = (editorPane.editorKit as DocumentationHtmlEditorKit).viewFactory
  private var imageResolver: DocumentationImageResolver? = null
  private val linkHandler: DocumentationLinkHandler
  private val cs = CoroutineScope(Dispatchers.EDT)
  private val contentListeners: MutableList<() -> Unit> = SmartList()

  override fun dispose() {
    htmlFactory.clearIcons()
    imageResolver = null
    cs.cancel()
  }

  init {
    scrollPane = DocumentationScrollPane()
    editorPane = DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane)) {
      imageResolver?.resolveImage(it)
    }
    editorPane.applyFontProps(DocumentationComponent.getQuickDocFontSize())
    scrollPane.setViewportView(editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(editorPane::applyFontProps))
    linkHandler = DocumentationLinkHandler.createAndRegister(editorPane, this, browser::navigateByLink)

    browser.ui = this
    Disposer.register(this, browser)
    Disposer.register(this, browser.addStateListener { request, result, _ ->
      applyStateLater(request, result)
    })

    for (action in linkHandler.createLinkActions()) {
      action.registerCustomShortcutSet(editorPane, this)
    }
    registerBackForwardActions(editorPane)

    val contextMenu = PopupHandler.installPopupMenu(
      editorPane,
      PRIMARY_GROUP_ID,
      "documentation.pane.content.menu"
    )
    Disposer.register(this) { editorPane.removeMouseListener(contextMenu) }

    DataManager.registerDataProvider(editorPane, this)

    fetchingProgress()
  }

  override fun getData(dataId: String): Any? {
    return when {
      DOCUMENTATION_BROWSER.`is`(dataId) -> browser
      SELECTED_QUICK_DOC_TEXT.`is`(dataId) -> editorPane.selectedText?.replace(160.toChar(), ' ') // IDEA-86633
      else -> null
    }
  }

  fun setBackground(color: Color): Disposable {
    val editorBG = editorPane.background
    editorPane.background = color
    return Disposable {
      editorPane.background = editorBG
    }
  }

  fun addContentListener(listener: () -> Unit): Disposable {
    EDT.assertIsEdt()
    contentListeners.add(listener)
    return Disposable {
      EDT.assertIsEdt()
      contentListeners.remove(listener)
    }
  }

  private fun fireContentChanged() {
    for (listener in contentListeners) {
      listener.invoke()
    }
  }

  private fun applyStateLater(request: DocumentationRequest, asyncData: Deferred<DocumentationData?>) {
    // to avoid flickering: don't show ""Fetching..." message right away, give a chance for documentation to load
    val fetchingMessage = cs.launch {
      delay(DEFAULT_UI_RESPONSE_TIMEOUT)
      fetchingProgress()
    }
    asyncData.invokeOnCompletion {
      fetchingMessage.cancel()
    }
    cs.launch {
      val data = try {
        asyncData.await()
      }
      catch (e: IndexNotReadyException) {
        null // normal situation, nothing to do
      }
      applyState(request, data)
    }
  }

  private fun applyState(request: DocumentationRequest, data: DocumentationData?) {
    htmlFactory.clearIcons()
    imageResolver = null
    if (data == null) {
      showMessage(CodeInsightBundle.message("no.documentation.found"))
      return
    }
    imageResolver = data.imageResolver
    val presentation = request.presentation
    val locationChunk = getDefaultLocationChunk(presentation)
    val linkChunk = linkChunk(presentation.presentableText, data)
    val decorated = decorate(data.html, locationChunk, linkChunk)
    val scrollingPosition = data.anchor?.let(ScrollingPosition::Anchor) ?: ScrollingPosition.Reset
    update(decorated, scrollingPosition)
  }

  private fun getDefaultLocationChunk(presentation: TargetPresentation): HtmlChunk? {
    return presentation.locationText?.let { locationText ->
      presentation.locationIcon?.let { locationIcon ->
        val iconKey = htmlFactory.registerIcon(locationIcon)
        HtmlChunk.fragment(
          HtmlChunk.tag("icon").attr("src", iconKey),
          HtmlChunk.nbsp(),
          HtmlChunk.text(locationText)
        )
      } ?: HtmlChunk.text(locationText)
    }
  }

  private fun fetchingProgress() {
    showMessage(CodeInsightBundle.message("javadoc.fetching.progress"))
  }

  private fun showMessage(message: @Nls String) {
    val element = HtmlChunk.div()
      .setClass("content-only")
      .addText(message)
      .wrapWith("body")
      .wrapWith("html")
    update(element.toString(), ScrollingPosition.Reset)
  }

  fun update(text: @Nls String, scrollingPosition: ScrollingPosition) {
    EDT.assertIsEdt()
    if (editorPane.text == text) {
      return
    }
    editorPane.text = text
    fireContentChanged()
    SwingUtilities.invokeLater {
      when (scrollingPosition) {
        ScrollingPosition.Keep -> {
          // do nothing
        }
        ScrollingPosition.Reset -> {
          editorPane.scrollRectToVisible(Rectangle(0, 0))
          if (ScreenReader.isActive()) {
            editorPane.caretPosition = 0
          }
        }
        is ScrollingPosition.Anchor -> {
          UIUtil.scrollToReference(editorPane, scrollingPosition.anchor)
        }
      }
    }
  }

  fun uiSnapshot(): UISnapshot {
    val viewRect = scrollPane.viewport.viewRect
    val highlightedLink = linkHandler.highlightedLink
    return {
      linkHandler.highlightLink(highlightedLink)
      editorPane.scrollRectToVisible(viewRect)
      if (ScreenReader.isActive()) {
        editorPane.caretPosition = 0
      }
    }
  }
}
