// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.*
import com.intellij.codeInsight.documentation.DocumentationManager.SELECTED_QUICK_DOC_TEXT
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.lang.documentation.ide.actions.DOCUMENTATION_BROWSER
import com.intellij.lang.documentation.ide.actions.PRIMARY_GROUP_ID
import com.intellij.lang.documentation.ide.actions.registerBackForwardActions
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationPage
import com.intellij.lang.documentation.ide.impl.DocumentationPageContent
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.PopupHandler
import com.intellij.util.SmartList
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JScrollPane
import kotlin.coroutines.EmptyCoroutineContext

internal class DocumentationUI(
  project: Project,
  val browser: DocumentationBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane

  private val icons = mutableMapOf<String, Icon>()
  private var imageResolver: DocumentationImageResolver? = null
  private val linkHandler: DocumentationLinkHandler
  private val cs = CoroutineScope(EmptyCoroutineContext)
  private val contentListeners: MutableList<() -> Unit> = SmartList()

  init {
    cs.launch(Dispatchers.EDT + CoroutineName("DocumentationUI content update")) {
      browser.pageFlow.collectLatest { page ->
        handlePage(page)
      }
    }
  }

  override fun dispose() {
    cs.cancel("DocumentationUI disposal")
    clearImages()
  }

  init {
    scrollPane = DocumentationScrollPane()
    editorPane = DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane), {
      imageResolver?.resolveImage(it)
    }, { icons[it] })
    editorPane.applyFontProps(DocumentationComponent.getQuickDocFontSize())
    scrollPane.setViewportView(editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(editorPane::applyFontProps))
    linkHandler = DocumentationLinkHandler.createAndRegister(editorPane, this, browser::navigateByLink)

    browser.ui = this
    Disposer.register(this, browser)

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

  private fun clearImages() {
    icons.clear()
    imageResolver = null
  }

  private suspend fun handlePage(page: DocumentationPage) {
    val presentation = page.request.presentation
    page.contentFlow.collectLatest {
      handleContent(presentation, it)
    }
  }

  private suspend fun handleContent(presentation: TargetPresentation, pageContent: DocumentationPageContent?) {
    when (pageContent) {
      null -> {
        // to avoid flickering: don't show ""Fetching..." message right away, give a chance for documentation to load
        delay(DEFAULT_UI_RESPONSE_TIMEOUT) // this call will be immediately cancelled once a new emission happens
        clearImages()
        showMessage(CodeInsightBundle.message("javadoc.fetching.progress"))
      }
      DocumentationPageContent.Empty -> {
        clearImages()
        showMessage(CodeInsightBundle.message("no.documentation.found"))
      }
      is DocumentationPageContent.Content -> {
        clearImages()
        handleContent(presentation, pageContent)
      }
    }
  }

  private suspend fun handleContent(presentation: TargetPresentation, pageContent: DocumentationPageContent.Content) {
    val content = pageContent.content
    imageResolver = content.imageResolver
    val locationChunk = getDefaultLocationChunk(presentation)
    val linkChunk = linkChunk(presentation.presentableText, pageContent.links)
    val decorated = decorate(content.html, locationChunk, linkChunk)
    update(decorated, pageContent.uiState)
  }

  private fun getDefaultLocationChunk(presentation: TargetPresentation): HtmlChunk? {
    return presentation.locationText?.let { locationText ->
      presentation.locationIcon?.let { locationIcon ->
        val iconKey = registerIcon(locationIcon)
        HtmlChunk.fragment(
          HtmlChunk.tag("icon").attr("src", iconKey),
          HtmlChunk.nbsp(),
          HtmlChunk.text(locationText)
        )
      } ?: HtmlChunk.text(locationText)
    }
  }

  private fun registerIcon(icon: Icon): String {
    val key = icons.size.toString()
    icons[key] = icon
    return key
  }

  private suspend fun showMessage(message: @Nls String) {
    val element = HtmlChunk.div()
      .setClass("content-only")
      .addText(message)
      .wrapWith("body")
      .wrapWith("html")
    update(element.toString(), UIState.Reset)
  }

  private suspend fun update(text: @Nls String, uiState: UIState?) {
    EDT.assertIsEdt()
    if (editorPane.text == text) {
      return
    }
    editorPane.text = text
    fireContentChanged()
    if (uiState == null) {
      return
    }
    yield()
    applyUIState(uiState)
  }

  private fun applyUIState(uiState: UIState) {
    when (uiState) {
      UIState.Reset -> {
        editorPane.scrollRectToVisible(Rectangle(0, 0))
        if (ScreenReader.isActive()) {
          editorPane.caretPosition = 0
        }
      }
      is UIState.ScrollToAnchor -> {
        UIUtil.scrollToReference(editorPane, uiState.anchor)
      }
      is UIState.RestoreFromSnapshot -> {
        uiState.snapshot.invoke()
      }
    }
  }

  fun uiSnapshot(): UISnapshot {
    EDT.assertIsEdt()
    val viewRect = scrollPane.viewport.viewRect
    val highlightedLink = linkHandler.highlightedLink
    return {
      EDT.assertIsEdt()
      linkHandler.highlightLink(highlightedLink)
      editorPane.scrollRectToVisible(viewRect)
      if (ScreenReader.isActive()) {
        editorPane.caretPosition = 0
      }
    }
  }
}
