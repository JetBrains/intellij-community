// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionContextElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.Interactive
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JComponent

internal class ExceptionShowCaseAction : DumbAwareAction() {


  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(true) {
      init {
        init()
      }


      override fun createCenterPanel(): JComponent = panel {
        row {
          button("Interactive Exception from Coroutine Background") {
            ApplicationManager.getApplication().service<MyService>().scope.launch(Dispatchers.IO + Interactive("Test interactive action")) {
              throw Exception("Exception from interactive coroutine BG")
            }
          }
          button("Interactive Exception from Coroutine EDT") {
            ApplicationManager.getApplication().service<MyService>().scope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
              throw Exception("Exception from interactive coroutine FG")
            }
          }
          button("Interactive Exception from EDT") {
            throw Exception("Exception from EDT")
          }
          button("Interactive Exception from Modal EDT") {
            runWithModalProgressBlocking(e.project!!, "...") {
              throw Exception("Exception from modal EDT")
            }
          }
        }
      }
    }.show()

  }

  override fun update(e: AnActionEvent) {
    this.templatePresentation.text = "Show Exception"
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@Service
private class MyService(val scope: CoroutineScope)