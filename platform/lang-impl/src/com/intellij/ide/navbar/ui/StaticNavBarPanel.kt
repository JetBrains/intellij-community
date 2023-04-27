// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.ide.*
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.attachAsChildTo
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformLatest
import java.awt.BorderLayout
import java.awt.Window

internal class StaticNavBarPanel(
  private val project: Project,
  private val cs: CoroutineScope,
  private val updateRequests: Flow<Any>,
  private val requestNavigation: (NavBarVmItem) -> Unit,
) : JBPanel<StaticNavBarPanel>(BorderLayout()), Activatable {

  private val _window: MutableStateFlow<Window?> = MutableStateFlow(null)
  private val _vm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val model: NavBarVm? get() = _vm.value

  override fun hideNotify() {
    _window.value = null
  }

  override fun showNotify() {
    _window.value = UIUtil.getWindow(this)
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
    val vm = NavBarVmImpl(
      this@supervisorScope,
      initialItems = defaultModel(project),
      contextItems = contextItems(window),
      requestNavigation,
    )
    _vm.value = vm
    try {
      awaitCancellation()
    }
    finally {
      _vm.value = null
    }
  }

  private fun contextItems(window: Window): Flow<List<NavBarVmItem>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    return updateRequests.transformLatest {
      dataContext(window, panel = this@StaticNavBarPanel)?.let {
        emit(contextModel(it, project))
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
