// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.OpenSourceUtil
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service(Service.Level.APP)
private class AutoScrollToSourceTaskManager : Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(ApplicationManager.getApplication(), this)
  }

  companion object {

    @JvmStatic
    fun getInstance(): AutoScrollToSourceTaskManager = ApplicationManager.getApplication().service()

    private suspend fun ActionCallback.suspend() = suspendCancellableCoroutine { continuation ->
      doWhenDone {
        continuation.resume(Unit)
      }.doWhenRejected { message ->
        continuation.resumeWithException(RuntimeException(message))
      }
    }

    private fun AutoScrollToSourceHandler.canAutoScrollTo(file: VirtualFile?) =
      file == null || isAutoScrollEnabledFor(file)
  }

  override fun dispose() {
    scope.cancel()
  }

  fun scheduleScrollToSource(
    handler: AutoScrollToSourceHandler,
    dataContext: DataContext,
  ) {
    scope.launch(Dispatchers.EDT) {
      PlatformDataKeys.TOOL_WINDOW.getData(dataContext)
        ?.getReady(handler)
        ?.suspend()

      val navigatable = withContext(Dispatchers.IO) {
        readAction {
          if (handler.canAutoScrollTo(CommonDataKeys.VIRTUAL_FILE.getData(dataContext)))
            CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext)?.singleOrNull()
          else
            null
        }
      }

      if (navigatable != null) {
        OpenSourceUtil.navigateToSource(false, true, navigatable)
      }
    }
  }
}