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
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicBoolean
import com.intellij.openapi.application.ApplicationManager

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
      joinAlarm() // for the autopopup handler's alarm, or the restartCompletion's invokeLater
      joinCompletion()
    }
  }

  protected void joinCompletion() {
    joinCommit() // file copy commit in background

    def controller = AutoPopupController.getInstance(getProject())
    controller.executePendingRequests();

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
        joinAlarm() // for invokeLater in CompletionProgressIndicator.stop()
        return
      }
      Thread.sleep(10)
    }
    printThreadDump()
    fail("Too long completion")
  }

  private def joinCommit() {
    final AtomicBoolean committed = new AtomicBoolean()
    edt {
      PsiDocumentManager.getInstance(project).cancelAndRunWhenAllCommitted("wait for all comm") {
        ApplicationManager.application.invokeLater { committed.set(true) }
      }
    }
    while (!committed.get()) {
      UIUtil.pump();
    }
  }

  protected void joinAlarm() {
    joinCommit()
    edt { PlatformTestUtil.waitForAlarm(CodeInsightSettings.instance.AUTO_LOOKUP_DELAY)}
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
