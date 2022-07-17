// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext

internal class DocumentationToolWindowUI(
  project: Project,
  val ui: DocumentationUI,
  private val content: Content,
) : Disposable {

  val browser: DocumentationBrowser get() = ui.browser

  val contentComponent: JComponent = JPanel(BorderLayout()).also {
    it.add(ui.scrollPane, BorderLayout.CENTER)
  }

  val editorPane: DocumentationEditorPane get() = ui.editorPane

  private var reusable: Disposable?

  val isReusable: Boolean
    get() {
      EDT.assertIsEdt()
      return reusable != null
    }

  private val autoUpdater = DocumentationToolWindowUpdater(project, browser)
  private var autoUpdate: Disposable? = null

  val isAutoUpdate: Boolean
    get() {
      EDT.assertIsEdt()
      return autoUpdate != null
    }

  private val cs = CoroutineScope(EmptyCoroutineContext)

  // Disposable tree:
  // content
  // > this
  // - > ui
  // - > autoUpdate
  // - > reuse
  // - - > asterisk content tab updater
  // - > content tab updater (after tab is kept)
  // - > search handler
  init {
    content.putUserData(TW_UI_KEY, this)
    Disposer.register(content, this)
    Disposer.register(this, ui)

    reusable = cs.updateContentTab(browser, content, asterisk = true).also {
      Disposer.register(this, it)
    }
    Disposer.register(this, DocumentationSearchHandler(this))
  }

  override fun dispose() {
    cs.cancel("DocumentationToolWindowUI disposal")
    content.putUserData(TW_UI_KEY, null)
  }

  fun toggleAutoUpdate(state: Boolean) {
    check(isReusable)
    EDT.assertIsEdt()
    if (state) {
      check(!isAutoUpdate)
      autoUpdate = UiNotifyConnector(ui.scrollPane, autoUpdater).also {
        Disposer.register(this, it)
      }
    }
    else {
      check(isAutoUpdate)
      Disposer.dispose(checkNotNull(autoUpdate))
      autoUpdate = null
    }
  }

  fun pauseAutoUpdate(): Disposable {
    check(isAutoUpdate)
    return autoUpdater.pause()
  }

  fun keep() {
    Disposer.dispose(checkNotNull(reusable))
    reusable = null
    Disposer.register(this, cs.updateContentTab(browser, content, asterisk = false))
    autoUpdate?.let {
      Disposer.dispose(it)
      autoUpdate = null
    }
  }
}

internal val Content.toolWindowUI: DocumentationToolWindowUI get() = checkNotNull(getUserData(TW_UI_KEY))

internal val Content.isReusable: Boolean get() = toolWindowUI.isReusable

private val TW_UI_KEY: Key<DocumentationToolWindowUI> = Key.create("documentation.tw.ui")

private fun CoroutineScope.updateContentTab(browser: DocumentationBrowser, content: Content, asterisk: Boolean): Disposable {
  val updateJob = launch(Dispatchers.EDT + CoroutineName("DocumentationToolWindowUI content update")) {
    browser.pageFlow.collectLatest { page ->
      // Wait for content a bit to avoid showing the title of the new page while the old page is still displayed.
      // Either the title will update together with the content, or the title will update together with "Fetching" message.
      withTimeoutOrNull(DEFAULT_UI_RESPONSE_TIMEOUT) {
        page.waitForContent()
      }
      val presentation = page.request.presentation
      content.icon = presentation.icon
      content.displayName = if (asterisk) "* ${presentation.presentableText}" else presentation.presentableText
    }
  }
  return Disposable(updateJob::cancel)
}
