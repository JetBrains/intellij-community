// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.debugger.engine.MockDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.mockJDI.MockStackFrame
import com.intellij.debugger.mockJDI.MockVirtualMachine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager.createPointer
import com.intellij.xdebugger.impl.dfaassist.DfaResult
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.util.streamex.EntryStream
import one.util.streamex.StreamEx
import java.util.function.BiConsumer

/**
 * Runs one DfaAssist test: [configure] gets the text without the hint markup and returns the element at the caret.
 * [configure] runs on the EDT. The test mocks the frame with [mockValues] and compares the hints with the markup of [text].
 */
internal fun runDfaAssistTest(
  project: Project,
  disposable: Disposable,
  text: String,
  mockValues: BiConsumer<in MockVirtualMachine, in MockStackFrame>,
  context: String?,
  configure: (filteredText: String) -> PsiElement,
) = runBlockingMaybeCancellable {
  val filteredText = text.replace("/\\*\\w+\\*/".toRegex(), "")
  val element = withContext(Dispatchers.EDT) {
    configure(filteredText)
  }

  val vm = MockVirtualMachine()
  val frame = MockStackFrame(vm, element)
  mockValues.accept(vm, frame)

  val process = MockDebugProcess(project, vm, disposable)
  val pointer = readAction { createPointer(element) }
  val runner = withDebugContext(process.managerThread) {
    val vmProxy = VirtualMachineProxyImpl.getCurrent()
    val threadProxy = vmProxy.allThreads().firstOrNull() ?: return@withDebugContext null
    val frameProxy = StackFrameProxyImpl(threadProxy, frame, 1)
    createDfaRunner(frameProxy, pointer)
  }

  TestCase.assertNotNull(context, runner)
  val result = readAction { runner!!.computeHints() }
  readAction {
    assertDfaAssistResult(text, context, result, filteredText)
  }
}

internal fun findDfaAssistElementAtCaret(file: PsiFile, editor: Editor, context: String?): PsiElement {
  val element = file.findElementAt(editor.caretModel.offset)
  TestCase.assertNotNull(context, element)
  return element!!
}

internal fun assertDfaAssistResult(text: String, context: String?, dfaResult: DfaResult, filteredText: String) {
  val fileText = filteredText.replace("<caret>", "")
  val hintStream = EntryStream.of(dfaResult.hints)
    .mapKeys { it.textRange.endOffset }
    .mapValues { "/*$it*/" }
  val unreachableStream = StreamEx.of(dfaResult.unreachable)
    .flatMapToEntry { mapOf(it.startOffset to "/*unreachable_start*/", it.endOffset to "/*unreachable_end*/") }
  val result = hintStream
    .append(unreachableStream)
    .sortedBy { it.key }
    .prepend(0, "")
    .append(fileText.length, "")
    .chain { StreamEx.of(it) }
    .pairMap { prev, next -> StreamEx.of(fileText.substring(prev.key, next.key), next.value) }
    .flatMap { it }
    .joining()
  val expectedText = text.replace("<caret>", "")
  TestCase.assertEquals(context, expectedText, result)
}
