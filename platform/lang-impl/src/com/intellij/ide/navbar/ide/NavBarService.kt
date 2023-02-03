// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.actions.NavBarActionHandler
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.isModuleContentRoot
import com.intellij.ide.navbar.impl.pathToItem
import com.intellij.ide.navbar.ui.NewNavBarPanel
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.ide.navbar.ui.showHint
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.childScope
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Window
import javax.swing.JComponent

@Service(PROJECT)
internal class NavBarService(private val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavBarService = project.service()
  }

  @Suppress("DEPRECATION")
  @OptIn(ExperimentalCoroutinesApi::class)
  private val coroutineScope: CoroutineScope = project.coroutineScope.childScope(
    Dispatchers.Default.limitedParallelism(1) // allows reasoning about the ordering
  )

  override fun dispose() {
    coroutineScope.cancel()
  }

  private val staticNavBarVm = StaticNavBarVmImpl(coroutineScope = coroutineScope,
                                                  project = project,
                                                  initiallyVisible = UISettings.getInstance().isNavbarShown())
  private var floatingBarJob: Job? = null

  fun uiSettingsChanged(uiSettings: UISettings) {
    if (uiSettings.isNavbarShown()) {
      floatingBarJob?.cancel()
    }
    staticNavBarVm.isVisible = uiSettings.isNavbarShown()
  }

  val staticNavBarPanel: JComponent by lazy(LazyThreadSafetyMode.NONE) {
    EDT.assertIsEdt()
    StaticNavBarPanel(coroutineScope, staticNavBarVm, project)
  }

  fun jumpToNavbar(dataContext: DataContext) {
    val navBarVm = staticNavBarVm.vm.value
    if (navBarVm != null) {
      navBarVm.selectTail()
      navBarVm.showPopup()
    }
    else {
      showFloatingNavbar(dataContext)
    }
  }

  fun selectTail() {
    staticNavBarVm.vm.value?.selectTail()
  }

  private fun showFloatingNavbar(dataContext: DataContext) {
    if (floatingBarJob != null) {
      return
    }

    val job = coroutineScope.launch(ModalityState.current().asContextElement()) {
      val model = contextModel(dataContext, project).ifEmpty {
        defaultModel(project)
      }
      val barScope = this@launch
      val vm = NavBarVmImpl(cs = barScope, project, model, activityFlow = emptyFlow())
      withContext(Dispatchers.EDT) {
        val component = NewNavBarPanel(barScope, vm, project, true)
        while (component.componentCount == 0) {
          // wait while panel will fill itself with item components
          yield()
        }
        showHint(dataContext, project, component)
        vm.selectTail()
        vm.showPopup()
      }
    }

    floatingBarJob = job

    job.invokeOnCompletion {
      floatingBarJob = null
    }
  }
}

internal suspend fun focusModel(project: Project): List<NavBarVmItem> {
  val ctx = focusDataContext()
  if (ctx.getData(NavBarActionHandler.NAV_BAR_ACTION_HANDLER) != null) {
    // ignore updates while nav bar has focus
    return emptyList()
  }
  val window: Window? = WindowManager.getInstance().getFrame(project)
  if (window != null && !window.isFocused) {
    // IDEA-307406, IDEA-304798 Skip event when window is out of focus (user is in a popup)
    return emptyList()
  }
  return contextModel(ctx, project)
}

/**
 * Use this API to dump current state of navigation bar in tests
 * Currently used in Rider
 */
@TestOnly
@Internal
suspend fun dumpContextModel(ctx: DataContext, project: Project) : List<String> {
  return contextModel(ctx, project).map { it.presentation.text }
}

private suspend fun contextModel(ctx: DataContext, project: Project): List<NavBarVmItem> {
  if (CommonDataKeys.PROJECT.getData(ctx) != project) {
    return emptyList()
  }
  try {
    return readAction {
      contextModelInner(ctx)
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (pce: ProcessCanceledException) {
    throw pce
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
    NavBarVmItem(it.createPointer(), it.presentation(), it.isModuleContentRoot(), it.javaClass)
  }
}

internal suspend fun defaultModel(project: Project): List<NavBarVmItem> {
  return readAction {
    val item = ProjectNavBarItem(project)
    listOf(NavBarVmItem(item.createPointer(), item.presentation(), true, item.javaClass))
  }
}
