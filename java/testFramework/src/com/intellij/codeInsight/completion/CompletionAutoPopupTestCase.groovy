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

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil

/**
 * @author peter
 */
abstract class CompletionAutoPopupTestCase extends LightCodeInsightFixtureTestCase {
  @Override protected void setUp() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable(){
                                 @Override
                                 void run() {
                                   superSetUp()
                                 }

                                 })
    CompletionAutoPopupHandler.ourTestingAutopopup = true
  }
  void superSetUp() {
    super.setUp()
  }
  void superTearDown() {
    super.tearDown()
  }

  @Override protected void tearDown() {
    CompletionAutoPopupHandler.ourTestingAutopopup = false
    UIUtil.invokeAndWaitIfNeeded(new Runnable(){
                                 @Override
                                 void run() {
                                   superTearDown()
                                 }

                                 })
  }

  protected void doHighlighting() {
    UIUtil.invokeAndWaitIfNeeded({ myFixture.doHighlighting() } as Runnable)
  }

  void type(String s) {
    for (i in 0..<s.size()) {
      final c = s.charAt(i)
      myFixture.type(c)
      ApplicationManager.application.invokeAndWait({ PlatformTestUtil.waitForAlarm(0) } as Runnable, ModalityState.NON_MODAL) // for the autopopup handler's alarm, or the restartCompletion's invokeLater
      ApplicationManager.application.invokeAndWait({ PlatformTestUtil.waitForAlarm(0) } as Runnable, ModalityState.NON_MODAL) // for invokeLater in CompletionProgressIndicator.stop()
    }
  }

  @Override protected void runTest() {
    runTestBare()
  }

  @Override protected boolean runInDispatchThread() {
    return false;
  }

  @Override protected void invokeTestRunnable(Runnable runnable) {
    runnable.run()
  }

  LookupImpl getLookup() {
    LookupManager.getActiveLookup(myFixture.getEditor())
  }

}
