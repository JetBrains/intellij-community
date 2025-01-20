// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.ui

import com.intellij.openapi.project.Project
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.vm.NavBarVm
import com.intellij.platform.navbar.frontend.vm.impl.NavBarVmImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Window
import javax.swing.JComponent

fun staticNavBarPanel(
  project: Project,
  initialItems: suspend () -> List<NavBarVmItem>,
  contextItems: (Window, JComponent) -> Flow<List<NavBarVmItem>>,
  requestNavigation: (NavBarVmItem) -> Unit,
): JComponent {

  val staticNavBarVm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val panel: JComponent = StaticNavBarPanel(project, staticNavBarVm)

  suspend fun handleWindow(window: Window): Nothing = supervisorScope {
    val vm = NavBarVmImpl(
      this@supervisorScope,
      initialItems = initialItems(),
      contextItems = contextItems(window, panel),
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

  panel.launchOnShow("static nav bar window") {
    val window = ComponentUtil.getWindow(panel)
                 ?: return@launchOnShow
    withContext(Dispatchers.Default) {
      handleWindow(window)
    }
  }

  return panel
}

internal typealias StaticNavBarPanelVm = StateFlow<NavBarVm?>

class StaticNavBarPanel(
  private val project: Project,
  private val _vm: StaticNavBarPanelVm,
) : JBPanel<StaticNavBarPanel>(BorderLayout()) {

  val model: NavBarVm? get() = _vm.value

  init {
    launchOnShow("static nav bar vm") {
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
