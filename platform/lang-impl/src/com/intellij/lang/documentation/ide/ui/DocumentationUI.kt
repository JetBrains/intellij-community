// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane
import com.intellij.codeInsight.documentation.DocumentationLinkHandler
import com.intellij.codeInsight.documentation.DocumentationManager.SELECTED_QUICK_DOC_TEXT
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.codeInsight.documentation.DocumentationScrollPane
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
import com.intellij.ui.FontSizeModel
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JScrollPane

internal class DocumentationUI(
  project: Project,
  val browser: DocumentationBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane
  val fontSize: FontSizeModel = DocumentationFontSizeModel()

  private val icons = mutableMapOf<String, Icon>()
  private var imageResolver: DocumentationImageResolver? = null
  private val linkHandler: DocumentationLinkHandler
  private val cs = CoroutineScope(Dispatchers.EDT)
  private val myContentUpdates = MutableSharedFlow<ContentUpdateKind>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val contentUpdates: Flow<Unit> = myContentUpdates.map {}

  private enum class ContentUpdateKind {
    FETCHING,
    EMPTY,
    CONTENT,
  }

  init {
    scrollPane = DocumentationScrollPane()
    editorPane = DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane), {
      imageResolver?.resolveImage(it)
    }, { icons[it] })
    scrollPane.setViewportView(editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(fontSize))
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

    cs.launch(CoroutineName("DocumentationUI content update")) {
      browser.pageFlow.collectLatest { page ->
        handlePage(page)
      }
    }
    cs.launch(CoroutineName("DocumentationUI font size update"), start = CoroutineStart.UNDISPATCHED) {
      fontSize.updates.collect {
        editorPane.applyFontProps(it)
      }
    }
  }

  override fun dispose() {
    cs.cancel("DocumentationUI disposal")
    clearImages()
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

  /**
   * Waits until UI has something to show. One of possible results:
   * - content was not loaded after [DEFAULT_UI_RESPONSE_TIMEOUT] => "Fetching..." is shown;
   * - content was loaded and it's empty => "No documentation" is shown;
   * - content was loaded => content is shown.
   *
   * @return `true` if the loaded content is empty,
   * or `false` if the content is not yet loaded or if the content is not empty
   */
  suspend fun waitForContentUpdate(): Boolean {
    return myContentUpdates.first() == ContentUpdateKind.EMPTY
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
        showMessage(ContentUpdateKind.FETCHING, CodeInsightBundle.message("javadoc.fetching.progress"))
      }
      DocumentationPageContent.Empty -> {
        clearImages()
        showMessage(ContentUpdateKind.EMPTY, CodeInsightBundle.message("no.documentation.found"))
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
    update(ContentUpdateKind.CONTENT, decorated, pageContent.uiState)
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

  private suspend fun showMessage(kind: ContentUpdateKind, message: @Nls String) {
    val element = HtmlChunk.div()
      .setClass("content-only")
      .addText(message)
      .wrapWith("body")
      .wrapWith("html")
    update(kind, element.toString(), UIState.Reset)
  }

  private suspend fun update(kind: ContentUpdateKind, text: @Nls String, uiState: UIState?) {
    EDT.assertIsEdt()
    if (editorPane.text == text) {
      return
    }
    editorPane.text = text
    myContentUpdates.emit(kind)
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
