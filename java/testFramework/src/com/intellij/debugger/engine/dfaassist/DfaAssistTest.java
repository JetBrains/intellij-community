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
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A facility to test DfaAssist
 */
public abstract class DfaAssistTest extends LightPlatformCodeInsightTestCase {
  protected void doTest(String text, BiConsumer<MockVirtualMachine, MockStackFrame> mockValues, String fileName) {
    String filteredText = text.replaceAll("/\\*\\w+\\*/", "");
    configureFromFileText(fileName, filteredText);
    PsiFile file = getFile();
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    MockVirtualMachine vm = new MockVirtualMachine();
    MockStackFrame frame = new MockStackFrame(vm, element);
    mockValues.accept(vm, frame);

    Ref<DebuggerDfaRunner> runnerRef = Ref.create();
    MockDebugProcess process = new MockDebugProcess(getProject(), vm, getTestRootDisposable());
    process.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        ThreadReferenceProxyImpl threadProxy = ContainerUtil.getFirstItem(process.getVirtualMachineProxy().allThreads());
        StackFrameProxyImpl frameProxy = new StackFrameProxyImpl(threadProxy, frame, 1);
        DebuggerDfaRunner runner = ReadAction.compute(() -> DfaAssist.createDfaRunner(frameProxy, element));
        runnerRef.set(runner);
      }
    });

    DebuggerDfaRunner runner = runnerRef.get();
    assertNotNull(runner);
    DebuggerDfaListener interceptor = runner.interpret();
    assertNotNull(interceptor);
    Map<PsiElement, DfaHint> hints = interceptor.computeHints();

    String fileText = filteredText.replace("<caret>", "");
    String result = EntryStream.of(hints)
      .mapKeys(ex -> ex.getTextRange().getEndOffset())
      .mapValues(hint -> "/*" + hint + "*/")
      .sorted(Map.Entry.comparingByKey())
      .prepend(0, "")
      .append(fileText.length(), "")
      .chain(StreamEx::of)
      .pairMap((prev, next) -> StreamEx.of(fileText.substring(prev.getKey(), next.getKey()), next.getValue()))
      .flatMap(Function.identity())
      .joining();
    String expectedText = text.replace("<caret>", "");
    assertEquals(expectedText, result);
  }
}
