/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
abstract class CompletionAutoPopupTestCase extends LightCodeInsightFixtureTestCase {
  private CompletionAutoPopupTester myTester

  @Override protected void setUp() {
    edt { superSetUp() }
    myTester = new CompletionAutoPopupTester(myFixture)
  }
  void superSetUp() {
    super.setUp()
  }
  void superTearDown() {
    super.tearDown()
  }

  @Override protected void tearDown() {
    edt { superTearDown() }
  }

  protected void doHighlighting() {
    edt { myFixture.doHighlighting() }
  }

  void type(String s) {
    myTester.typeWithPauses(s)
  }

  protected void joinCompletion() {
    myTester.joinCompletion()
  }

  protected def joinCommit(Closure c1={}) {
    myTester.joinCommit(c1)
  }

  protected void joinAutopopup() {
    myTester.joinAutopopup()
  }

  protected def joinAlarm() {
    myTester.joinAlarm()
  }

  @Override protected void runTest() {
    myTester.runWithAutoPopupEnabled { runTestBare() }
  }

  @Override protected boolean runInDispatchThread() {
    return false;
  }

  @Override protected void invokeTestRunnable(Runnable runnable) {
    runnable.run()
  }

  LookupImpl getLookup() {
    (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor())
  }

}
