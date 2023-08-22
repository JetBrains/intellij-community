// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBPanel
import com.intellij.util.attachAsChildTo
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import java.awt.Window
import javax.swing.JComponent

internal class StaticNavBarPanel(
  private val project: Project,
  private val cs: CoroutineScope,
  private val provider: suspend (CoroutineScope, Window, JComponent) -> NavBarVm
) : JBPanel<StaticNavBarPanel>(BorderLayout()), Activatable {

  private val _window: MutableStateFlow<Window?> = MutableStateFlow(null)
  private val _vm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val model: NavBarVm? get() = _vm.value

  override fun hideNotify() {
    _window.value = null
  }

  override fun showNotify() {
    _window.value = ComponentUtil.getWindow(this)
  }

  init {
    UiNotifyConnector.installOn(this, this, false)

    // This coroutine will GC-ed together with the `StaticNavBarPanel` instance as long as it isn't referenced by anything else,
    // `attachAsChildTo` makes sure the coroutine refs are cleaned.
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch {
      _window.collectLatest { window ->
        if (window != null) {
          coroutineScope {
            val windowScope = this@coroutineScope
            cs.launch {
              attachAsChildTo(windowScope)
              handleWindow(window)
            }
          }
        }
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.EDT) {
      _vm.collectLatest { vm ->
        if (vm != null) {
          handleVm(vm)
        }
      }
    }
  }

  private suspend fun handleWindow(window: Window): Nothing = supervisorScope {
    val vm = provider.invoke(this@supervisorScope, window, this@StaticNavBarPanel)
    _vm.value = vm
    try {
      awaitCancellation()
    }
    finally {
      _vm.value = null
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
