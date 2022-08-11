// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.ui.NavBarWrapper
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.swing.JComponent


sealed class NavBarEvent {
  object PresentationChangeEvent : NavBarEvent()
  object ModelChangeEvent : NavBarEvent()
}

class NavBarRootPaneExtension(project: Project) : IdeRootPaneNorthExtension(), Disposable {
  override fun copy(): IdeRootPaneNorthExtension {
    return this
  }

  private val cs = CoroutineScope(CoroutineName("NavigationBarStore"))
  private val myEventFlow = MutableSharedFlow<NavBarEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val navigationBar = NavigationBar(project, cs, myEventFlow.asSharedFlow())

  private val wrapperPanel = NavBarWrapper(project, navigationBar.getComponent())

  override fun getComponent(): JComponent = wrapperPanel.component

  init {
    val listener = NavBarListener(project, myEventFlow)
    Disposer.register(this, listener)

    Disposer.register(this, wrapperPanel)
  }

  override fun dispose() {
    cs.coroutineContext.cancel()
  }

  override fun uiSettingsChanged(settings: UISettings) {
  }

  override fun getKey(): String {
    return NAVBAR_WIDGET_KEY
  }

  fun show(dataContext: DataContext) {
    navigationBar.show(dataContext)
  }

  companion object {
    const val PANEL_KEY = "NavBarPanel2"
    const val NAVBAR_WIDGET_KEY = "NavBarPanel2"
  }
}
