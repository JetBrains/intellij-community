// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.pathToItem
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext


@Service(PROJECT)
internal class NavBarService(val myProject: Project) : Disposable {

  private val cs: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  private val staticPanel = JPanel(BorderLayout())
  private var staticNavigationBar: NavigationBar? = null

  private val staticBarShown = MutableStateFlow(UISettings.getInstance().isNavbarShown())

  override fun dispose() {
    cs.cancel()
  }

  init {
    cs.launch(Dispatchers.EDT) {
      staticBarShown.collect { show ->
        if (show) show() else hide()
      }
    }
  }

  fun uiSettingsChanged(uiSettings: UISettings) {
    staticBarShown.tryEmit(uiSettings.isNavbarShown())
  }

  fun getStaticNavbarPanel() = staticPanel

  fun jumpToNavbar(dataContext: DataContext) {
    (staticNavigationBar ?: createFloatingNavbar(dataContext)).focusTail()
  }

  private fun createFloatingNavbar(dataContext: DataContext): NavigationBar {
    val childScope = cs.childScope()

    val initialModel = runBlocking {
      contextModel(dataContext)
    }

    val popupNavbar = NavigationBar(myProject, childScope, initialModel, dataContext)
    FloatingModeHelper.showHint(dataContext, popupNavbar, myProject)
    return popupNavbar
  }

  private fun show() {
    if (staticNavigationBar != null) {
      return
    }
    val initialModel = runBlocking {
      focusModel(myProject)
    }

    val staticBar = NavigationBar(myProject, cs.childScope(), initialModel)
    Disposer.register(this, staticBar)
    staticNavigationBar = staticBar
    staticPanel.add(staticBar.getPanel())
  }

  private fun hide() {
    staticNavigationBar?.let {
      staticPanel.removeAll()
      Disposer.dispose(it)
      staticNavigationBar = null
    }
  }

}

internal suspend fun focusModel(project: Project): List<NavBarVmItem> {
  val ctx = focusDataContext()
  return contextModel(ctx, project)
}

private suspend fun contextModel(ctx: DataContext, project: Project): List<NavBarVmItem> {
  if (CommonDataKeys.PROJECT.getData(ctx) != project) {
    return emptyList()
  }
  return contextModel(ctx).ifEmpty {
    defaultModel(project)
  }
}

private suspend fun contextModel(ctx: DataContext): List<NavBarVmItem> {
  try {
    return readAction {
      contextModelInner(ctx)
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (t: Throwable) {
    LOG.error(t)
    return emptyList()
  }
}

private fun contextModelInner(ctx: DataContext): List<NavBarVmItem> {
  val contextItem = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
                    ?: return emptyList()
  return contextItem.pathToItem().toVmItems()
}

internal fun List<NavBarItem>.toVmItems(): List<NavBarVmItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return map {
    NavBarVmItem(it.createPointer(), it.presentation(), it.javaClass)
  }
}

private suspend fun defaultModel(project: Project): List<NavBarVmItem> {
  return readAction {
    val item = ProjectNavBarItem(project)
    listOf(NavBarVmItem(item.createPointer(), item.presentation(), item.javaClass))
  }
}
