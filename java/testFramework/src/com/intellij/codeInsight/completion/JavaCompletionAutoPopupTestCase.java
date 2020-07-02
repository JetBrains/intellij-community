// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class JavaCompletionAutoPopupTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected CompletionAutoPopupTester myTester;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTester = new CompletionAutoPopupTester(myFixture);
  }

  public void type(String s) {
    myTester.typeWithPauses(s);
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    myTester.runWithAutoPopupEnabled(testRunnable);
  }

  public LookupImpl getLookup() {
    return (LookupImpl)myFixture.getLookup();
  }
}
