// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.StaticNavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JPanel

internal class StaticNavBarPanel(
  cs: CoroutineScope,
  staticVm: StaticNavBarVm,
  project: Project,
) : JPanel(BorderLayout()) {

  init {
    EDT.assertIsEdt()
    cs.launch(Dispatchers.EDT) {
      staticVm.vm.collectLatest { vm ->
        if (vm == null) {
          removeAll()
        }
        else {
          coroutineScope {
            add(NewNavBarPanel(this@coroutineScope, vm, project, isFloating = false))
          }
        }
      }
    }
  }
}
