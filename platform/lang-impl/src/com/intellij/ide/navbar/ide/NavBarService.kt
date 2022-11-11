// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.BorderLayout
import javax.swing.JPanel


internal fun isNavbarShown() = with(UISettings.getInstance()) { showNavigationBar && !presentationMode }

@Service(PROJECT)
internal class NavBarService(val myProject: Project) : Disposable {

  private val cs = CoroutineScope(CoroutineName("NavigationBarScope"))

  private val staticPanel = JPanel(BorderLayout())
  private var staticNavigationBar: NavigationBar? = null

  private val staticBarShown = MutableStateFlow(isNavbarShown())

  override fun dispose() {
    cs.coroutineContext.cancel()
  }

  init {
    cs.launch(Dispatchers.EDT) {
      staticBarShown.collect { show ->
        if (show) show() else hide()
      }
    }
  }

  fun uiSettingsChanged(uiSettings: UISettings) {
    staticBarShown.tryEmit(uiSettings.showNavigationBar && !uiSettings.presentationMode)
  }

  fun getStaticNavbarPanel() = staticPanel

  fun jumpToNavbar(dataContext: DataContext) {
    (staticNavigationBar ?: createFloatingNavbar(dataContext)).focusTail()
  }

  private fun createFloatingNavbar(dataContext: DataContext): NavigationBar {
    val childScope = cs.childScope()
    val popupNavbar = NavigationBar(myProject, childScope, dataContext)
    FloatingModeHelper.showHint(dataContext, popupNavbar, myProject)
    return popupNavbar
  }

  private fun show() {
    if (staticNavigationBar != null) {
      return
    }
    val staticBar = NavigationBar(myProject, cs.childScope())
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

