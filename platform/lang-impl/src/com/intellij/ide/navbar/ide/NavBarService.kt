// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.isModuleContentRoot
import com.intellij.ide.navbar.impl.pathToItem
import com.intellij.ide.navbar.ui.NewNavBarPanel
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.ide.navbar.ui.showHint
import com.intellij.ide.ui.UISettings
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import com.intellij.util.flow.throttle
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

@Service(PROJECT)
internal class NavBarService(private val project: Project, cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavBarService = project.service()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val cs: CoroutineScope = cs.childScope(
    Dispatchers.Default.limitedParallelism(1) // allows reasoning about the ordering
  )

  private val visible: MutableStateFlow<Boolean> = MutableStateFlow(UISettings.getInstance().isNavbarShown())
  private val updateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)

  init {
    cs.launch {
      visible.collectLatest { visible ->
        if (visible) {
          // If [visible] is `true`, then at least 1 nav bar is shown
          // => subscribe to activityFlow once and share events between all models.
          activityFlow(project)
            .throttle(DEFAULT_UI_RESPONSE_TIMEOUT)
            .collect(updateRequests)
        }
      }
    }
  }

  private var floatingBarJob: Job? = null

  fun uiSettingsChanged(uiSettings: UISettings) {
    if (uiSettings.isNavbarShown()) {
      floatingBarJob?.cancel()
    }
    visible.value = uiSettings.isNavbarShown()
  }

  fun createNavBarPanel(): JComponent {
    EDT.assertIsEdt()
    return StaticNavBarPanel(project, cs, updateRequests)
  }

  fun showFloatingNavbar(dataContext: DataContext) {
    if (floatingBarJob != null) {
      return
    }

    val job = cs.launch(ModalityState.current().asContextElement()) {
      val model = contextModel(dataContext, project).ifEmpty {
        defaultModel(project)
      }
      val barScope = this@launch
      val vm = NavBarVmImpl(cs = barScope, project, model, contextItems = emptyFlow())
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

/**
 * Use this API to dump current state of navigation bar in tests
 * Currently used in Rider
 */
@TestOnly
@Internal
suspend fun dumpContextModel(ctx: DataContext, project: Project): List<String> {
  return contextModel(ctx, project).map { it.presentation.text }
}

internal suspend fun contextModel(ctx: DataContext, project: Project): List<NavBarVmItem> {
  if (CommonDataKeys.PROJECT.getData(ctx) != project) {
    return emptyList()
  }
  return try {
    readAction {
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
    emptyList()
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
