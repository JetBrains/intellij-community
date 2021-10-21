// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.content.Content
import com.intellij.util.ui.update.UiNotifyConnector
import javax.swing.JComponent

internal class DocumentationToolWindowUI(
  project: Project,
  private val ui: DocumentationUI,
  private val content: Content,
) : Disposable {

  override fun dispose() {}

  val browser: DocumentationBrowser get() = ui.browser

  val contentComponent: JComponent get() = ui.scrollPane

  private var preview: Disposable?

  // Disposable tree:
  // content
  // > this
  // - > ui
  // - > preview
  // - - > auto-updater
  // - - > asterisk content tab updater
  // - - > content user data cleaner
  // - > content tab updater (after preview is turned off)
  init {
    Disposer.register(content, this)
    Disposer.register(this, ui)

    val preview = Disposer.newDisposable(this, "documentation preview").also {
      this.preview = it
    }

    Disposer.register(preview, UiNotifyConnector(ui.scrollPane, DocumentationToolWindowUpdater(project, browser)))
    Disposer.register(preview, browser.addStateListener { request, _, byLink ->
      if (byLink && Registry.`is`("documentation.v2.turn.off.preview.by.links")) {
        turnOffPreview()
        return@addStateListener
      }
      val presentation = request.presentation
      content.icon = presentation.icon
      content.displayName = "* ${presentation.presentableText}"
    })

    content.putUserData(TW_UI_KEY, this)
    Disposer.register(preview) {
      content.putUserData(TW_UI_KEY, null)
    }
  }

  fun turnOffPreview() {
    val preview = requireNotNull(this.preview) {
      "the preview was turned off already"
    }
    this.preview = null
    Disposer.dispose(preview)
    Disposer.register(this, updateContentTab(browser, content))
  }
}

internal val Content.toolWindowUI: DocumentationToolWindowUI? get() = getUserData(TW_UI_KEY)

private val TW_UI_KEY: Key<DocumentationToolWindowUI> = Key.create("documentation.tw.ui")

private fun updateContentTab(browser: DocumentationBrowser, content: Content): Disposable {
  return browser.addStateListener { request, _, _ ->
    val presentation = request.presentation
    content.icon = presentation.icon
    content.displayName = presentation.presentableText
  }
}
