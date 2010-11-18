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
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

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

  void type(String s) {
    myFixture.type(s)
    ApplicationManager.application.invokeAndWait({ println "wait1" } as Runnable, ModalityState.NON_MODAL) // for the autopopup's alarm
    ApplicationManager.application.invokeAndWait({ println "wait2" } as Runnable, ModalityState.NON_MODAL) // for the restartCompletion's invokeLater
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

  Lookup getLookup() {
    LookupManager.getActiveLookup(myFixture.getEditor())
  }

}
