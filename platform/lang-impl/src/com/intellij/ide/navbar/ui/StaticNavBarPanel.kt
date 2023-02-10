// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.navbar.vm.StaticNavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import javax.swing.JPanel

internal class StaticNavBarPanel(
  cs: CoroutineScope,
  staticVm: StaticNavBarVm,
  private val project: Project,
) : JPanel(BorderLayout()) {

  init {
    EDT.assertIsEdt()
    cs.launch(Dispatchers.EDT) {
      staticVm.vm.collectLatest { vm ->
        if (vm != null) {
          showPanel(vm)
        }
      }
    }
  }

  private suspend fun showPanel(vm: NavBarVm): Nothing {
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
