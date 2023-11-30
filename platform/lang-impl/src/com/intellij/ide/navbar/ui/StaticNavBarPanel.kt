// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.ide.*
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.model.Pointer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.ui.components.JBPanel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.BorderLayout
import java.awt.Window
import javax.swing.JComponent

// GlobalScope is used to avoid attaching a child, which is never cancelled, to a service scope.
// Without GlobalScope, each call to `staticNavBarPanel` produces a new child coroutine,
// which may leak because the component is never disposed, as it's simply removed from the UI hierarchy.
// Removal from the hierarchy triggers `window.value = null`,
//   which cancels the `handleWindow` coroutine,
//   which triggers `staticNavBarVm.value = null`,
//   which cancels `StaticNavBarPanel.handleVm` coroutine.
// Once the component is removed from hierarchy, `window` and `staticNavBarVm` flows never emit,
// their subscriptions are never resumed (and never scheduled), so they are GC-ed together with the component.
@OptIn(DelicateCoroutinesApi::class)
fun staticNavBarPanel(
  project: Project,
  cs: CoroutineScope,
  updateRequests: Flow<Any>,
  requestNavigation: (Pointer<out NavBarItem>) -> Unit,
): JComponent {

  val staticNavBarVm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val panel: JComponent = StaticNavBarPanel(project, GlobalScope, staticNavBarVm)
  val window: StateFlow<Window?> = trackCurrentWindow(panel)

  fun contextItems(window: Window): Flow<List<NavBarVmItem>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    return updateRequests.transformLatest {
      dataContext(window, panel)?.let {
        emit(contextModel(it, project))
      }
    }
  }

  suspend fun handleWindow(window: Window): Nothing = supervisorScope {
    val vm = NavBarVmImpl(
      this@supervisorScope,
      initialItems = defaultModel(project),
      contextItems = contextItems(window),
    )
    vm.activationRequests.onEach(requestNavigation).launchIn(this)
    staticNavBarVm.value = vm
    try {
      awaitCancellation()
    }
    finally {
      staticNavBarVm.value = null
    }
  }

  // This coroutine will GC-ed together with the `StaticNavBarPanel` instance as long as it isn't referenced by anything else,
  // `attachAsChildTo` makes sure the coroutine refs are cleaned.
  GlobalScope.launch {
    window.collectLatest { window ->
      if (window != null) {
        coroutineScope {
          val windowScope = this@coroutineScope
          cs.launch(start = CoroutineStart.UNDISPATCHED) {
            attachAsChildTo(windowScope)
            handleWindow(window)
          }
        }
      }
    }
  }

  return panel
}

internal typealias StaticNavBarPanelVm = StateFlow<NavBarVm?>

internal class StaticNavBarPanel(
  private val project: Project,
  cs: CoroutineScope,
  private val _vm: StaticNavBarPanelVm,
) : JBPanel<StaticNavBarPanel>(BorderLayout()) {

  val model: NavBarVm? get() = _vm.value

  init {
    cs.launch(Dispatchers.EDT) {
      _vm.collectLatest { vm ->
        if (vm != null) {
          handleVm(vm)
        }
      }
    }
  }

  private suspend fun handleVm(vm: NavBarVm): Nothing = supervisorScope {
    val panel = NewNavBarPanel(this@supervisorScope, vm, project, isFloating = false)
    add(panel)
    try {
      awaitCancellation()
    }
    finally {
      remove(panel)
    }
  }
}
