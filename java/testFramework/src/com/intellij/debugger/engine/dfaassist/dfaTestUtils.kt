// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.debugger.engine.MockDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.runBlocking

@Suppress("RAW_RUN_BLOCKING")
internal fun getDfaRunnerNow(element: PsiElement, process: MockDebugProcess, frame: MockStackFrame): DebuggerDfaRunner? {
  val pointer = SmartPointerManager.createPointer<PsiElement?>(element)
  val managerThread = process.managerThread
  return runBlocking {
    withDebugContext(managerThread) {
      val threadProxy = process.virtualMachineProxy.allThreads().firstOrNull() ?: return@withDebugContext null
      val frameProxy = StackFrameProxyImpl(threadProxy, frame, 1)
      createDfaRunner(frameProxy, pointer)
    }
  }
}
