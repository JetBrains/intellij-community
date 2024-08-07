// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.debugger.engine.MockDebugProcess;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.mockJDI.MockStackFrame;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.dfaassist.DfaHint;
import com.intellij.xdebugger.impl.dfaassist.DfaResult;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A facility to test DfaAssist
 */
public abstract class DfaAssistTest extends LightPlatformCodeInsightTestCase {
  protected void doTest(String text, BiConsumer<? super MockVirtualMachine, ? super MockStackFrame> mockValues, String fileName) {
    doTest(text, mockValues, fileName, "");
  }

  protected void doTest(String text, BiConsumer<? super MockVirtualMachine, ? super MockStackFrame> mockValues, String fileName,
                        @Nullable String context) {
    String filteredText = text.replaceAll("/\\*\\w+\\*/", "");
    configureFromFileText(fileName, filteredText);
    PsiFile file = getFile();
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    assertNotNull(context, element);
    MockVirtualMachine vm = new MockVirtualMachine();
    MockStackFrame frame = new MockStackFrame(vm, element);
    mockValues.accept(vm, frame);

    Ref<DebuggerDfaRunner> runnerRef = Ref.create();
    MockDebugProcess process = new MockDebugProcess(getProject(), vm, getTestRootDisposable());
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
    process.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        ThreadReferenceProxyImpl threadProxy = ContainerUtil.getFirstItem(process.getVirtualMachineProxy().allThreads());
        StackFrameProxyImpl frameProxy = new StackFrameProxyImpl(threadProxy, frame, 1);
        DebuggerDfaRunner runner = DfaAssist.createDfaRunner(frameProxy, pointer);
        runnerRef.set(runner);
      }
    });

    DebuggerDfaRunner runner = runnerRef.get();
    assertNotNull(context, runner);
    DfaResult dfaResult;
    try {
      dfaResult = ReadAction.nonBlocking(() -> runner.computeHints()).submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    Map<PsiElement, DfaHint> hints = dfaResult.hints;

    String fileText = filteredText.replace("<caret>", "");
    EntryStream<Integer, String> hintStream = EntryStream.of(hints)
      .mapKeys(ex -> ex.getTextRange().getEndOffset())
      .mapValues(hint -> "/*" + hint + "*/");
    EntryStream<Integer, String> unreachableStream = StreamEx.of(dfaResult.unreachable)
      .flatMapToEntry(range -> Map.of(range.getStartOffset(), "/*unreachable_start*/", range.getEndOffset(), "/*unreachable_end*/"));
    String result = hintStream
      .append(unreachableStream)
      .sorted(Map.Entry.comparingByKey())
      .prepend(0, "")
      .append(fileText.length(), "")
      .chain(StreamEx::of)
      .pairMap((prev, next) -> StreamEx.of(fileText.substring(prev.getKey(), next.getKey()), next.getValue()))
      .flatMap(Function.identity())
      .joining();
    String expectedText = text.replace("<caret>", "");
    assertEquals(context, expectedText, result);
  }
}
