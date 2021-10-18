// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.*
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.codeInsight.documentation.DocumentationManager.getLink
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationData
import com.intellij.lang.documentation.ide.actions.*
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

internal class DocumentationUI(
  project: Project,
  val browser: DocumentationBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane

  private val htmlFactory: DocumentationHtmlFactory get() = (editorPane.editorKit as DocumentationHtmlEditorKit).viewFactory
  private val linkHandler: DocumentationLinkHandler
  private val cs = CoroutineScope(Dispatchers.EDT)
  private val contentListeners: MutableList<() -> Unit> = SmartList()

  override fun dispose() {
    htmlFactory.clearIcons()
    cs.cancel()
  }

  init {
    scrollPane = DocumentationScrollPane()
    editorPane = DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane)) { null }
    editorPane.applyFontProps(DocumentationComponent.getQuickDocFontSize())
    scrollPane.setViewportView(editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(editorPane::applyFontProps))
    linkHandler = DocumentationLinkHandler.createAndRegister(editorPane, this, browser::navigateByLink)

    browser.snapshooter = ::uiSnapshot
    Disposer.register(this, browser)
    Disposer.register(this, browser.addStateListener { request, result, _ ->
      applyStateLater(request, result)
    })

    val primaryActions = primaryActions()
    for (action in linkHandler.createLinkActions() + primaryActions) {
      action.registerCustomShortcutSet(editorPane, this)
    }

    val externalDocAction = ActionManager.getInstance().getAction(DOCUMENTATION_VIEW_EXTERNAL_ACTION_ID)
    val contextMenu = PopupHandler.installPopupMenu(
      editorPane,
      DefaultActionGroup(primaryActions + externalDocAction),
      "documentation.pane.content.menu"
    )
    Disposer.register(this) { editorPane.removeMouseListener(contextMenu) }

    DataManager.registerDataProvider(editorPane, this)

    fetchingProgress()
  }

  override fun getData(dataId: String): Any? {
    return when {
      DOCUMENTATION_BROWSER_DATA_KEY.`is`(dataId) -> browser
      DOCUMENTATION_HISTORY_DATA_KEY.`is`(dataId) -> browser.history
      DOCUMENTATION_TARGET_POINTER_KEY.`is`(dataId) -> browser.targetPointer
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
      applyState(request, asyncData.await())
      fireContentChanged()
    }
  }

  private fun applyState(request: DocumentationRequest, data: DocumentationData?) {
    htmlFactory.clearIcons()
    if (data == null) {
      showMessage(CodeInsightBundle.message("no.documentation.found"))
      return
    }
    val decorated = renderDocumentation(data, request) {
      htmlFactory.registerIcon(it)
    }
    update(decorated, data.anchor)
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
    update(element.toString(), null)
  }

  private sealed class AnchorOrRect {
    class Anchor(val anchor: String) : AnchorOrRect()
    class Rect(val rect: Rectangle) : AnchorOrRect()
  }

  private fun update(text: @Nls String, anchor: String?) {
    EDT.assertIsEdt()
    editorPane.text = text

    val anchorOrRect = when {
      DocumentationManagerProtocol.KEEP_SCROLLING_POSITION_REF == anchor -> AnchorOrRect.Rect(
        scrollPane.viewport.viewRect) // save current scroll position
      anchor != null -> AnchorOrRect.Anchor(anchor)
      else -> null
    }

    SwingUtilities.invokeLater {
      when (anchorOrRect) {
        is AnchorOrRect.Anchor -> UIUtil.scrollToReference(editorPane, anchorOrRect.anchor)
        is AnchorOrRect.Rect -> editorPane.scrollRectToVisible(anchorOrRect.rect)
        null -> {
          editorPane.scrollRectToVisible(Rectangle(0, 0))
          if (ScreenReader.isActive()) {
            editorPane.caretPosition = 0
          }
        }
      }
    }
  }

  private fun uiSnapshot(): UISnapshot {
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

  companion object {
    @Nls
    fun renderDocumentation(data: DocumentationData, request: DocumentationRequest, iconKeyProvider: (Icon) -> String): String {
      val presentation = request.presentation
      val locationChunk = presentation.locationText?.let { locationText ->
        presentation.locationIcon?.let { locationIcon ->
          val iconKey = iconKeyProvider(locationIcon)
          HtmlChunk.fragment(
            HtmlChunk.tag("icon").attr("src", iconKey),
            HtmlChunk.nbsp(),
            HtmlChunk.text(locationText)
          )
        } ?: HtmlChunk.text(locationText)
      }
      val linkChunk = getLink(presentation.presentableText, data.externalUrl)
      return decorate(data.html, locationChunk, linkChunk)
    }
  }
}
