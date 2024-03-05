// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil
import com.intellij.codeInsight.documentation.DocumentationLinkHandler
import com.intellij.codeInsight.documentation.DocumentationManager.SELECTED_QUICK_DOC_TEXT
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.codeInsight.documentation.DocumentationScrollPane
import com.intellij.codeInsight.hint.DefinitionSwitcher
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_CONTENT
import com.intellij.lang.documentation.ide.actions.PRIMARY_GROUP_ID
import com.intellij.lang.documentation.ide.actions.registerBackForwardActions
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationPage
import com.intellij.lang.documentation.ide.impl.DocumentationPageContent
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent.ContentUpdateKind
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.documentation.DOCUMENTATION_BROWSER
import com.intellij.platform.util.coroutines.flow.collectLatestUndispatched
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingTextTrimmer
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane

internal class DocumentationUI(
  val project: Project,
  val browser: DocumentationBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane
  val locationLabel: JLabel
  val fontSize: DocumentationFontSizeModel = DocumentationFontSizeModel()
  val switcherToolbarComponent: JComponent

  private var imageResolver: DocumentationImageResolver? = null
  private val linkHandler: DocumentationLinkHandler
  private val cs = CoroutineScope(Dispatchers.EDT)
  private val myContentUpdates = MutableSharedFlow<ContentUpdateKind>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val contentUpdates: SharedFlow<ContentUpdateKind> = myContentUpdates.asSharedFlow()
  private val myContentSizeUpdates = MutableSharedFlow<PopupUpdateEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val contentSizeUpdates: SharedFlow<PopupUpdateEvent> = myContentSizeUpdates.asSharedFlow()
  private val switcher: DefinitionSwitcher<DocumentationRequest>
  private var contentKind: ContentKind? = null

  init {
    scrollPane = DocumentationScrollPane()
    editorPane = DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane)) {
      imageResolver?.resolveImage(it)
    }
    Disposer.register(this, editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(fontSize))
    linkHandler = DocumentationLinkHandler.createAndRegister(editorPane, this, ::linkActivated)
    switcher = createSwitcher()
    switcherToolbarComponent = switcher.createToolbar().component.apply {
      border = JBUI.Borders.empty(DocumentationHtmlUtil.spaceBeforeParagraph,
                                  DocumentationHtmlUtil.contentOuterPadding - 4,
                                  0,
                                  DocumentationHtmlUtil.contentOuterPadding - 4)
    }
    val textTrimmer = SwingTextTrimmer.createCenterTrimmer(0.8f)
    locationLabel = object : JLabel() {
      override fun getToolTipText(): String? =
        if (textTrimmer.isTrimmed) text else null
    }.apply {
      iconTextGap = 6
      border = JBUI.Borders.empty(
        DocumentationHtmlUtil.spaceBeforeParagraph,
        2 + DocumentationHtmlUtil.contentOuterPadding + DocumentationHtmlUtil.contentInnerPadding,
        2 + DocumentationHtmlUtil.contentOuterPadding, DocumentationHtmlUtil.contentOuterPadding)
      putClientProperty(SwingTextTrimmer.KEY, textTrimmer)
    }
    scrollPane.setViewportView(editorPane, locationLabel)
    trackDocumentationBackgroundChange(this) {
      scrollPane.viewport.background = it
      locationLabel.background = it
      switcherToolbarComponent.background = it
    }

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

    fetchingMessage()
    cs.launch(CoroutineName("DocumentationUI content update"), start = CoroutineStart.UNDISPATCHED) {
      browser.pageFlow.collectLatestUndispatched { page ->
        handlePage(page)
      }
    }
    cs.launch(CoroutineName("DocumentationUI font size update"), start = CoroutineStart.UNDISPATCHED) {
      fontSize.updates.collect {
        editorPane.applyFontProps(it)
        myContentSizeUpdates.emit(PopupUpdateEvent.FontChanged)
      }
    }
    cs.launch(CoroutineName("DocumentationUI content size update emission"), start = CoroutineStart.UNDISPATCHED) {
      myContentUpdates.collect {
        myContentSizeUpdates.emit(PopupUpdateEvent.ContentChanged(it))
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

  fun trackDocumentationBackgroundChange(disposable: Disposable, onChange: (Color) -> Unit) {
    val job = cs.launch {
      editorPane.backgroundFlow.collectLatest {
        withContext(Dispatchers.EDT) {
          onChange(it)
        }
      }
    }
    Disposer.register(disposable) {
      job.cancel()
    }
  }

  private fun clearImages() {
    imageResolver = null
  }

  private suspend fun handlePage(page: DocumentationPage) {
    val presentation = page.request.presentation
    val i = switcher.elements.indexOf(page.request)
    if (i < 0) {
      switcher.elements = page.requests.toTypedArray()
      switcher.index = 0
    }
    else {
      switcher.index = i
    }
    updateSwitcherVisibility()
    page.contentFlow.collectLatest {
      handleContent(presentation, it)
    }
  }

  fun updateSwitcherVisibility() {
    val visible = switcher.elements.count() > 1
    switcherToolbarComponent.isVisible = visible
    scrollPane.border = JBUI.Borders.emptyTop(if (visible) 0 else DocumentationHtmlUtil.contentOuterPadding - DocumentationHtmlUtil.spaceBeforeParagraph)
  }

  private suspend fun handleContent(presentation: TargetPresentation, pageContent: DocumentationPageContent?) {
    when (pageContent) {
      null -> {
        // to avoid flickering: don't show ""Fetching..." message right away, give a chance for documentation to load
        delay(DEFAULT_UI_RESPONSE_TIMEOUT) // this call will be immediately cancelled once a new emission happens
        clearImages()
        fetchingMessage()
        applyUIState(UIState.Reset)
      }
      DocumentationPageContent.Empty -> {
        clearImages()
        noDocumentationMessage()
        applyUIState(UIState.Reset)
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
    val linkChunk = linkChunk(presentation.presentableText, pageContent.links)
    val decorated = decorate(content.html, null, linkChunk, pageContent.downloadSourcesLink)
    if (!updateContent(decorated, presentation, ContentKind.DocumentationPage)) {
      return
    }
    val uiState = pageContent.uiState
    if (uiState != null) {
      yield()
      applyUIState(uiState)
    }
  }

  private fun fetchingMessage() {
    updateContent(message(CodeInsightBundle.message("javadoc.fetching.progress")), null, ContentKind.InfoMessage)
  }

  private fun noDocumentationMessage() {
    updateContent(message(CodeInsightBundle.message("no.documentation.found")), null, ContentKind.InfoMessage)
  }

  private fun message(message: @Nls String): @Nls String {
    return HtmlChunk.div()
      .setClass(CLASS_CONTENT)
      .children(HtmlChunk.p().addText(message))
      .wrapWith("body")
      .wrapWith("html")
      .toString()
  }

  private fun updateContent(text: @Nls String,
                            presentation: TargetPresentation?,
                            newContentKind: ContentKind): Boolean {
    EDT.assertIsEdt()
    if (editorPane.text == text &&
        locationLabel.text == presentation?.locationText &&
        locationLabel.icon == presentation?.icon &&
        contentKind == newContentKind) {
      return false
    }
    val oldContentKind = contentKind
    contentKind = newContentKind
    editorPane.text = text
    if (presentation?.locationText != null) {
      locationLabel.text = presentation.locationText
      locationLabel.toolTipText = presentation.locationText
      locationLabel.icon = presentation.locationIcon
      locationLabel.isVisible = true
    }
    else {
      locationLabel.text = ""
      locationLabel.isVisible = false
    }
    check(myContentUpdates.tryEmit(
      when (newContentKind) {
        ContentKind.InfoMessage -> ContentUpdateKind.InfoMessage
        ContentKind.DocumentationPage -> if (oldContentKind != newContentKind)
          ContentUpdateKind.DocumentationPageOpened
        else
          ContentUpdateKind.DocumentationPageNavigated
      }))
    return true
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

  private fun linkActivated(href: String) {
    if (href.startsWith("#")) {
      UIUtil.scrollToReference(editorPane, href.removePrefix("#"))
    }
    else {
      browser.handleLink(href)
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

  private fun createSwitcher(): DefinitionSwitcher<DocumentationRequest> {
    val requests = browser.page.requests
    return DefinitionSwitcher(requests.toTypedArray(), scrollPane) {
      browser.resetBrowser(it)
    }
  }

  private enum class ContentKind {
    InfoMessage,
    DocumentationPage,
  }
}
