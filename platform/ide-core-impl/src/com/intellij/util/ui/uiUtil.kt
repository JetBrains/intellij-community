// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component



@ApiStatus.Experimental
interface UiReadExecutor {
  companion object {
    /**
     * Returns a handler for executing conflated computations that require EDT _and_ read access.
     *
     * This function is useful inside input events where the user would like to run computations synchronously,
     * but if it is not possible, the computation is delayed until it is possible to acquire read access.
     * Meanwhile, the handling of input event can continue without blocking.
     *
     * The runnable executed for an instance of [UiReadExecutor] are _conflated_ -- if it is not possible to run the provided runnables synchronously,
     * only the latest one gets executed.
     * @param component UI object which is used for managing lifetime of conflation. The runnables are executed only when the component is showing.
     * @param name name of the launching process for debugging purposes
     */
    @JvmStatic
    fun conflatedUiReadExecutor(component: Component, disposable: Disposable, name: String): UiReadExecutor {
      val flow: MutableStateFlow<Runnable> = MutableStateFlow(EmptyRunnable.INSTANCE)
      val job = component.launchOnShow("uiReadExecutor ($name)") {
        flow.collectLatest { runnable ->
          if (runnable === EmptyRunnable.INSTANCE) return@collectLatest
          withContext(Dispatchers.EDT) {
            runnable.run()
          }
        }
      }

      Disposer.register(disposable, {
        job.cancel()
      })

      return object: UiReadExecutor {
        override fun executeWithReadAccess(action: Runnable) {
          ThreadingAssertions.assertEventDispatchThread()
          val isSuccess = ApplicationManagerEx.getApplicationEx().tryRunReadAction(action)
          if (isSuccess) {
            return
          }
          assert(flow.tryEmit(action)) {
            "Emission in a state flow should always be successful"
          }
        }
      }
    }
  }

  @RequiresEdt
  fun executeWithReadAccess(@RequiresEdt @RequiresReadLock action: Runnable)
}
