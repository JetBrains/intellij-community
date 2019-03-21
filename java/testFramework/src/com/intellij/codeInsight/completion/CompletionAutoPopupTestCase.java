package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionAutoPopupTestCase extends LightCodeInsightFixtureTestCase {
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
  protected void invokeTestRunnable(@NotNull Runnable runnable) {
    myTester.runWithAutoPopupEnabled(runnable);
  }

  public LookupImpl getLookup() {
    return (LookupImpl)myFixture.getLookup();
  }

}
