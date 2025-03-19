// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.platform.navbar.frontend.ui.NewNavBarPanel
import com.intellij.platform.navbar.frontend.ui.showHint
import com.intellij.platform.navbar.frontend.ui.staticNavBarPanel
import com.intellij.platform.navbar.frontend.vm.impl.NavBarVmImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.serviceContainer.instance
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import java.awt.Window
import javax.swing.JComponent

@Service(PROJECT)
class NavBarService(private val project: Project, cs: CoroutineScope) {
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
      val service = instance<NavBarServiceDelegate>()
      visible.collectLatest { visible ->
        if (visible) {
          // If [visible] is `true`, then at least 1 nav bar is shown
          // => subscribe to activityFlow once and share events between all models.
          updateRequests.emit(Unit)
          service.activityFlow()
            .throttle(DEFAULT_UI_RESPONSE_TIMEOUT)
            .collect {
              updateRequests.emit(it)
            }
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
    return staticNavBarPanel(
      project,
      initialItems = ::defaultModel,
      contextItems = ::contextItems,
      requestNavigation = ::requestNavigation,
    )
  }

  private suspend fun defaultModel(): List<NavBarVmItem> {
    return listOf(
      project
        .service<NavBarServiceDelegate>()
        .defaultModel()
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun contextItems(window: Window, panel: JComponent): Flow<List<NavBarVmItem>> {
    return updateRequests.transformLatest {
      dataContext(window, panel)?.let {
        emit(contextModel(it, project))
      }
    }
  }

  fun showFloatingNavbar(dataContext: DataContext) {
    if (floatingBarJob != null) {
      return
    }

    val job = cs.launch(ModalityState.current().asContextElement()) {
      val model = contextModel(dataContext, project).ifEmpty {
        defaultModel()
      }
      val barScope = this@launch
      val vm = NavBarVmImpl(cs = barScope, model, contextItems = emptyFlow())
      vm.activationRequests.onEach(::requestNavigation).launchIn(this)
      withContext(Dispatchers.EDT) {
        val component = NewNavBarPanel(barScope, vm, project, true)
        while (component.componentCount == 0) {
          // wait while panel will fill itself with item components
          yield()
        }
        showHint(dataContext, project, component)
        vm.selectTail(true)
      }
    }

    floatingBarJob = job

    job.invokeOnCompletion {
      floatingBarJob = null
    }
  }

  private fun requestNavigation(item: NavBarVmItem) {
    cs.launch {
      instance<NavBarServiceDelegate>()
        .navigate(item)
      updateRequests.emit(Unit)
    }
  }
}

suspend fun contextModel(ctx: DataContext, project: Project): List<NavBarVmItem> {
  if (CommonDataKeys.PROJECT.getData(ctx) != project) {
    return emptyList()
  }
  return try {
    project
      .service<NavBarServiceDelegate>()
      .contextModel(ctx)
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (pce: ProcessCanceledException) {
    throw pce
  }
  catch (t: Throwable) {
    fileLogger().error(t)
    emptyList()
  }
}

fun UISettings.isNavbarShown(): Boolean {
  return showNavigationBar && !presentationMode
}
