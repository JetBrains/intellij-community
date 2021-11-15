// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import java.awt.BorderLayout

internal class DocumentationSearchHandler(
  private val toolWindowUI: DocumentationToolWindowUI,
) : Disposable {

  private val startSearchAction = object : DumbAwareAction() {

    init {
      shortcutSet = ActionUtil.getShortcutSet(IdeActions.ACTION_FIND)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = searchDisposable == null
    }

    override fun actionPerformed(e: AnActionEvent) {
      startSearch()
    }
  }

  private val stopSearchAction = object : DumbAwareAction() {

    init {
      shortcutSet = ActionUtil.getShortcutSet(IdeActions.ACTION_EDITOR_ESCAPE)
    }

    override fun actionPerformed(e: AnActionEvent) {
      stopSearch()
    }
  }

  init {
    startSearchAction.registerCustomShortcutSet(toolWindowUI.contentComponent, this)
  }

  override fun dispose() {}

  private var searchDisposable: Disposable? = null

  private fun startSearch() {
    val search = SearchModel(toolWindowUI.ui).also {
      Disposer.register(this, it)
      this.searchDisposable = it
    }
    stopSearchAction.registerCustomShortcutSet(search.searchField, search)

    val contentComponent = toolWindowUI.contentComponent

    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent): Unit = search.searchField.requestFocus()
    }.registerCustomShortcutSet(ActionUtil.getShortcutSet(IdeActions.ACTION_FIND), contentComponent, search)

    val navigationActions = search.createNavigationActions()
    for (action in navigationActions) {
      action.registerCustomShortcutSet(contentComponent, search)
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(
      "documentation search",
      DefaultActionGroup(navigationActions),
      true
    ).also {
      it.targetComponent = contentComponent
    }

    val searchPanel = panel {
      row {
        cell(search.searchField).horizontalAlign(HorizontalAlign.FILL).resizableColumn()
        cell(search.matchLabel)
        cell(toolbar.component)
      }
    }
    contentComponent.add(searchPanel, BorderLayout.NORTH)
    Disposer.register(search) {
      contentComponent.remove(searchPanel)
      contentComponent.revalidate()
    }

    search.searchField.requestFocus()
  }

  private fun stopSearch() {
    val searchDisposable = this.searchDisposable
    if (searchDisposable != null) {
      this.searchDisposable = null
      Disposer.dispose(searchDisposable)
    }
  }
}
