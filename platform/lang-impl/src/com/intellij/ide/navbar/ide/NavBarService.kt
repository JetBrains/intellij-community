// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.pathToItem
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.swing.JComponent
import kotlin.coroutines.EmptyCoroutineContext

@Service(PROJECT)
internal class NavBarService(private val myProject: Project) : Disposable {

  private val cs: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun dispose() {
    cs.cancel()
  }

  private val staticNavBarVm = StaticNavBarVmImpl(cs, myProject, UISettings.getInstance().isNavbarShown())

  fun uiSettingsChanged(uiSettings: UISettings) {
    staticNavBarVm.isVisible = uiSettings.isNavbarShown()
  }

  val staticNavBarPanel: JComponent by lazy(LazyThreadSafetyMode.NONE) {
    EDT.assertIsEdt()
    StaticNavBarPanel(cs, staticNavBarVm)
  }

  fun jumpToNavbar(dataContext: DataContext) {
    (staticNavBarVm.vm.value ?: createFloatingNavbar(dataContext)).selectTail()
  }

  private fun createFloatingNavbar(dataContext: DataContext): NavBarVmImpl {
    val childScope = cs.childScope()

    val initialModel = runBlocking {
      contextModel(dataContext)
    }

    val popupNavbar = NavBarVmImpl(myProject, childScope, initialModel, dataContext)
    FloatingModeHelper.showHint(dataContext, childScope, popupNavbar, myProject)
    return popupNavbar
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
