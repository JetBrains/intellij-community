// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.ide.*
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import java.awt.BorderLayout
import java.awt.Window

internal class StaticNavBarPanel(
  private val project: Project,
  private val cs: CoroutineScope,
  private val updateRequests: Flow<Any>,
  private val requestNavigation: (NavBarVmItem) -> Unit,
) : JBPanel<StaticNavBarPanel>(BorderLayout()), Activatable {

  init {
    UiNotifyConnector(this, this, false)
  }

  private var job: Job? = null

  override fun hideNotify() {
    EDT.assertIsEdt()
    job?.cancel()
  }

  override fun showNotify() {
    EDT.assertIsEdt()
    // compute the window once and pass it around instead of going through UI hierarchy on each event
    val window = UIUtil.getWindow(this)
                 ?: return
    val prev = job
    job = cs.launch {
      prev?.join()
      withModel(window) { vm ->
        showPanel(vm)
      }
    }
  }

  @Volatile
  var model: NavBarVm? = null
    private set

  private suspend fun withModel(window: Window, block: suspend (NavBarVm) -> Nothing): Nothing = supervisorScope {
    LOG.assertTrue(model === null, "model was not cleared correctly")
    val vm = NavBarVmImpl(
      this@supervisorScope,
      initialItems = defaultModel(project),
      contextItems = contextItems(window),
      requestNavigation,
    )
    model = vm
    try {
      block(vm)
    }
    finally {
      LOG.assertTrue(model === vm, "model was changed")
      model = null
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

  private suspend fun showPanel(vm: NavBarVm): Nothing = withContext(Dispatchers.EDT) {
    supervisorScope {
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
}
