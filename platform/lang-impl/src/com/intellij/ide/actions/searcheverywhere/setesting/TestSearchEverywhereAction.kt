// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.setesting

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import java.awt.Dimension

private const val LOCATION_SETTINGS_KEY = "se.contributors.test.dialog"

class TestSearchEverywhereAction: AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val content = SETestingPanel(collectContributors(e))
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
      .setProject(e.project)
      .setModalContext(false)
      .setCancelOnClickOutside(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelKeyEnabled(true)
      .setRequestFocus(true)
      .setCancelCallback { true }
      .setResizable(true)
      .setMovable(true)
      .setTitle(IdeBundle.message("searcheverywhere.test.dialog.title"))
      .setDimensionServiceKey(e.project, LOCATION_SETTINGS_KEY, true)
      .setLocateWithinScreenBounds(true)
      .createPopup()

    Disposer.register(popup, content)
    popup.setMinimumSize(Dimension(950, 600))
    popup.showInFocusCenter()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  private fun collectContributors(initEvent: AnActionEvent): List<SearchEverywhereContributor<*>> {
    val project = initEvent.project
    val res = mutableListOf<SearchEverywhereContributor<*>>()
    res.add(TopHitSEContributor(project, null, null))
    res.add(PSIPresentationBgRendererWrapper.wrapIfNecessary(RecentFilesSEContributor(initEvent)))
    //res.add(RunConfigurationsSEContributor(project, null) { mySearchEverywhereUI.getSearchField().getText() })

    for (factory in SearchEverywhereContributor.EP_NAME.extensionList) {
      if (factory.isAvailable(project)) {
        val contributor = factory.createContributor(initEvent)
        res.add(contributor)
      }
    }

    return res
  }
}