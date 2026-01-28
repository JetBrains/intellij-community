// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.debugger.engine.MockDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.debugger.mockJDI.MockVirtualMachine
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager.createPointer
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.BiConsumer

internal fun DfaAssistTest.doTestInternal(
  project: Project,
  text: String,
  mockValues: BiConsumer<in MockVirtualMachine, in MockStackFrame>,
  fileName: String,
  context: String?,
) = runBlockingMaybeCancellable {
  val filteredText = text.replace("/\\*\\w+\\*/".toRegex(), "")
  val element = withContext(Dispatchers.EDT) {
    initializeTest(fileName, context, filteredText)
  }

  val vm = MockVirtualMachine()
  val frame = MockStackFrame(vm, element)
  mockValues.accept(vm, frame)

  val process = MockDebugProcess(project, vm, getTestRootDisposable())
  val pointer = readAction { createPointer(element) }
  val runner = withDebugContext(process.managerThread) {
    val threadProxy = process.virtualMachineProxy.allThreads().firstOrNull() ?: return@withDebugContext null
    val frameProxy = StackFrameProxyImpl(threadProxy, frame, 1)
    createDfaRunner(frameProxy, pointer)
  }

  TestCase.assertNotNull(context, runner)
  val result = readAction { runner!!.computeHints() }
  readAction {
    DfaAssistTest.assertResult(text, context, result, filteredText)
  }
}
