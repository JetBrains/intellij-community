// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.debugger.mockJDI.MockStackFrame;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.xdebugger.impl.dfaassist.DfaResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

import static com.intellij.debugger.engine.dfaassist.DfaTestUtilsKt.assertDfaAssistResult;
import static com.intellij.debugger.engine.dfaassist.DfaTestUtilsKt.findDfaAssistElementAtCaret;
import static com.intellij.debugger.engine.dfaassist.DfaTestUtilsKt.runDfaAssistTest;

/**
 * A facility to test DfaAssist
 *
 * @deprecated Use {@link DfaAssistFixtureKt#dfaAssistFixture} with a JUnit 6 test.
 */
@Deprecated
public abstract class DfaAssistTest extends LightPlatformCodeInsightTestCase {

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected boolean isRunInCommand() {
    return false;
  }

  protected void doTest(String text, BiConsumer<? super MockVirtualMachine, ? super MockStackFrame> mockValues, String fileName) {
    doTest(text, mockValues, fileName, "");
  }

  @NotNull PsiElement initializeTest(String fileName, @Nullable String context, String filteredText) {
    configureFromFileText(fileName, filteredText);
    return findDfaAssistElementAtCaret(getFile(), getEditor(), context);
  }

  protected void doTest(String text, BiConsumer<? super MockVirtualMachine, ? super MockStackFrame> mockValues, String fileName,
                        @Nullable String context) {
    runDfaAssistTest(getProject(), getTestRootDisposable(), text, mockValues, context,
                     filteredText -> initializeTest(fileName, context, filteredText));
  }


  static void assertResult(String text, @Nullable String context, DfaResult dfaResult, String filteredText) {
    assertDfaAssistResult(text, context, dfaResult, filteredText);
  }
}
