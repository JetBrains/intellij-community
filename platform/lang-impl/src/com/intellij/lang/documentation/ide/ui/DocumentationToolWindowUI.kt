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
  ui: DocumentationUI,
  private val content: Content,
) : Disposable {

  private var _ui: DocumentationUI? = ui
  private val ui get() = requireNotNull(_ui) { "already detached" }

  init {
    content.putUserData(TW_UI_KEY, this)
    Disposer.register(content, this)
    Disposer.register(this, UiNotifyConnector(ui.scrollPane, DocumentationToolWindowUpdater(project, ui.browser)))
    Disposer.register(this, browser.addStateListener { request, _, byLink ->
      if (byLink && Registry.`is`("documentation.v2.turn.off.preview.by.links")) {
        turnOffPreview()
        return@addStateListener
      }
      val presentation = request.presentation
      content.icon = presentation.icon
      content.displayName = "* ${presentation.presentableText}"
    })
  }

  override fun dispose() {
    val ui = _ui
    if (ui != null) {
      Disposer.dispose(ui)
      _ui = null
    }
    content.putUserData(TW_UI_KEY, null)
  }

  private fun detachUI(): DocumentationUI {
    val ui = ui
    _ui = null
    return ui
  }

  val browser: DocumentationBrowser get() = ui.browser

  val contentComponent: JComponent get() = ui.scrollPane

  fun turnOffPreview() {
    val ui = detachUI()
    Disposer.dispose(this)
    Disposer.register(content, ui)
    Disposer.register(content, updateContentTab(ui.browser, content))
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
