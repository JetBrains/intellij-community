// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
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
      val items = readAction {
        buildModel(dataContext)
      }
      items.ifEmpty {
        defaultModel(myProject)
      }
    }

    val popupNavbar = NavigationBar(myProject, childScope, initialModel, dataContext)
    FloatingModeHelper.showHint(dataContext, popupNavbar, myProject)
    return popupNavbar
  }

  private fun show() {
    if (staticNavigationBar != null) {
      return
    }
    val initialContext = runBlocking { focusDataContext() }

    val initialModel = runBlocking {
      val items = readAction {
        buildModel(initialContext)
      }
      items.ifEmpty {
        defaultModel(myProject)
      }
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

private suspend fun defaultModel(project: Project): List<NavBarVmItem> {
  return readAction {
    val item = ProjectNavBarItem(project)
    listOf(NavBarVmItem(item.createPointer(), item.presentation(), item.javaClass))
  }
}
