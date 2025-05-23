// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.wrapIncompatibleThreadStateException
import com.intellij.util.ThreeState
import com.intellij.util.suspendingLazy
import com.sun.jdi.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

internal fun StackFrameDescriptorImpl.canDropFrameSync(): ThreeState {
  return isSafeToDropFrame(uiIndex, unsureIfCallerFrameAbsent = true) { i ->
    methodOccurrence.getMethodOccurrence(i)?.method
  }
}

internal fun StackFrameDescriptorImpl.canDropFrameAsync(): CompletableFuture<Boolean> {
  val managerThread = frameProxy.virtualMachine.debugProcess.managerThread
  return managerThread.coroutineScope.future(Dispatchers.Default) {
    val lazyFrames = suspendingLazy {
      withDebugContext(managerThread) {
        try {
          frameProxy.threadProxy().frames()
        }
        catch (_: EvaluateException) {
          null
        }
      }
    }

    suspend fun computeMethod(frameIndex: Int): Method? = wrapIncompatibleThreadStateException {
      val frame = lazyFrames.getValue()?.getOrNull(frameIndex) ?: return null
      withDebugContext(managerThread) {
        val location = frame.locationAsync().await()
        DebuggerUtilsAsync.method(location).await()
      }
    }

    isSafeToDropFrame(uiIndex, unsureIfCallerFrameAbsent = false) { i ->
      methodOccurrence.getMethodOccurrence(i)?.method ?: computeMethod(i)
    }.toBoolean()
  }
}

private inline fun isSafeToDropFrame(frameIndex: Int, unsureIfCallerFrameAbsent: Boolean, methodProvider: (Int) -> Method?): ThreeState {
  for (i in 0..frameIndex + 1) {
    val method = methodProvider(i)
    if (method == null) {
      if (unsureIfCallerFrameAbsent && i == frameIndex + 1) {
        return ThreeState.UNSURE
      }
      return ThreeState.NO
    }
    if (method.isNative) return ThreeState.NO
  }
  return ThreeState.YES
}
