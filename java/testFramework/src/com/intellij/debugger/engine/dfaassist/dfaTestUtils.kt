// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.debugger.engine.MockDebugProcess
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

internal fun getDfaRunnerNow(element: PsiElement, process: MockDebugProcess, frame: MockStackFrame): DebuggerDfaRunner? {
  var runner: DebuggerDfaRunner? = null
  val pointer = SmartPointerManager.createPointer<PsiElement?>(element)
  process.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
    override suspend fun actionSuspend() {
      val threadProxy = process.getVirtualMachineProxy().allThreads().firstOrNull() ?: return
      val frameProxy = StackFrameProxyImpl(threadProxy, frame, 1)
      runner = createDfaRunner(frameProxy, pointer)
    }
  })
  return runner
}