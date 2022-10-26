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
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.swing.JComponent

@Service(PROJECT)
internal class NavBarService(private val project: Project) : Disposable {

  companion object {

    @JvmStatic
    fun getInstance(project: Project): NavBarService = project.service()
  }

  private val cs: CoroutineScope = CoroutineScope(SupervisorJob())

  override fun dispose() {
    cs.cancel()
  }

  private val staticNavBarVm = StaticNavBarVmImpl(cs, project, UISettings.getInstance().isNavbarShown())

  fun uiSettingsChanged(uiSettings: UISettings) {
    staticNavBarVm.isVisible = uiSettings.isNavbarShown()
  }

  val staticNavBarPanel: JComponent by lazy(LazyThreadSafetyMode.NONE) {
    EDT.assertIsEdt()
    StaticNavBarPanel(cs, staticNavBarVm)
  }

  fun jumpToNavbar(dataContext: DataContext) {
    val navBarVm = staticNavBarVm.vm.value
    if (navBarVm != null) {
      navBarVm.selectTail()
    }
    else {
      showFloatingNavbar(dataContext)
    }
  }

  private fun showFloatingNavbar(dataContext: DataContext) {
    cs.launch(ModalityState.current().asContextElement()) {
      val model = contextModel(dataContext, project)
      val barScope = this@launch
      val vm = NavBarVmImpl(cs = barScope, project, model, activityFlow = emptyFlow())
      withContext(Dispatchers.EDT) {
        FloatingModeHelper.showHint(dataContext, barScope, vm, project)
        vm.selectTail()
      }
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

internal suspend fun defaultModel(project: Project): List<NavBarVmItem> {
  return readAction {
    val item = ProjectNavBarItem(project)
    listOf(NavBarVmItem(item.createPointer(), item.presentation(), item.javaClass))
  }
}
