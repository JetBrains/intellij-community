/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.openapi.editor.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class FoldingExceptionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void test() {
    doTest("FoldingExceptionTest.java");
  }

  @Test
  public void testAnother() {
    doTest("FoldingException2Test.java");
  }

  private void doTest(String fileName) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile("/codeInsight/folding/" + fileName);
    EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.configureSoftWraps(daemon.getFixture().getEditor(), 120));
    runFoldingPass();
    daemon.executeAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    runFoldingPass();
    // we just verify here that the operation completes normally - it was known to fail previously
  }

  private void runFoldingPass() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);
  }
}
