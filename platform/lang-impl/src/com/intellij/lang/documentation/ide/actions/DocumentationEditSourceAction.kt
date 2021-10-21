// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.util.OpenSourceUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

internal class DocumentationEditSourceAction : AnAction(), UpdateInBackground {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.dataContext.getData(DOCUMENTATION_TARGET_POINTER)?.dereference()?.navigatable != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val targetPointer = dataContext.getData(DOCUMENTATION_TARGET_POINTER) ?: return
    ReadAction.nonBlocking(Callable {
      targetPointer.dereference()?.navigatable
    }).finishOnUiThread(ModalityState.defaultModalityState()) { navigatable ->
      if (navigatable != null) {
        OpenSourceUtil.navigate(true, navigatable)
      }
    }.submit(AppExecutorUtil.getAppExecutorService())
    dataContext.getData(DOCUMENTATION_POPUP)?.cancel()
  }
}
