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

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author peter
 */
abstract class CompletionAutoPopupTestCase extends LightCodeInsightFixtureTestCase {
  @Override protected void setUp() {
    edt { superSetUp() }
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
    ((DocumentEx) myFixture.editor.document).setModificationStamp(0)  // to not let autopopup handler sneak in
    edt { superTearDown() }
  }

  protected void doHighlighting() {
    edt { myFixture.doHighlighting() }
  }

  void type(String s) {
    for (i in 0..<s.size()) {
      final c = s.charAt(i)
      myFixture.type(c)
      joinAutopopup() // for the autopopup handler's alarm, or the restartCompletion's invokeLater
      joinCompletion()
    }
  }

  protected void joinCompletion() {
    for (i in 0.1000) {
      if (i==999) {
        printThreadDump()
        fail("Could not wait for committed doc")
      }
      CompletionPhase phase = CompletionServiceImpl.getCompletionPhase()
      if (phase != com.intellij.codeInsight.completion.CompletionPhase.NoCompletion) break;
      Thread.sleep(10)
    }

    for (j in 1..4000) {
      LookupImpl l = null
      edt {
        l = LookupManager.getInstance(project).activeLookup
      }
      if (!l || !l.calculating) {
        edt {} // for invokeLater in CompletionProgressIndicator.stop()
        return
      }
      Thread.sleep(10)
    }
    printThreadDump()
    fail("Too long completion")
  }

  protected def joinCommit(Closure cl = {}) {
    final AtomicBoolean committed = new AtomicBoolean()
    edt {
      PsiDocumentManager.getInstance(project).cancelAndRunWhenAllCommitted("wait for all comm") {
        ApplicationManager.application.invokeLater {
          cl()
          committed.set(true) 
        }
      }
    }
    def start = System.currentTimeMillis()
    while (!committed.get()) {
      if (System.currentTimeMillis() - start >= 10000) {
        printThreadDump()
        fail('too long waiting for a document to be committed')
      }
      UIUtil.pump();
    }
  }

  protected void joinAutopopup() {
    joinAlarm();
    joinCommit() // physical document commit
    joinCommit() // file copy commit in background
  }

  protected def joinAlarm() {
    AutoPopupController.getInstance(getProject()).executePendingRequests()
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
    (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor())
  }

}
