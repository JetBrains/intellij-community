// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl.ExecutorGroupActionGroup
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class RunToolbarChooserAdditionGroup(private val executorGroup: ExecutorGroup<*>, process: RunToolbarProcess,
                                              childConverter: (Executor) -> AnAction) : ExecutorGroupActionGroup(executorGroup,
                                                                                                                  childConverter) {
  var myProcess: RunToolbarProcess? = null

  init {
    myProcess = process
    val presentation = templatePresentation
    presentation.text = executorGroup.getRunToolbarChooserText()
    isPopup = true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.project?.let {
      e.presentation.isEnabledAndVisible = !e.isActiveProcess()
    }
  }
}