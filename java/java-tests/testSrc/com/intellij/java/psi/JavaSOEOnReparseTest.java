/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.psi;

import com.intellij.codeInsight.daemon.CanChangeDocumentDuringHighlighting;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@SkipSlowTestLocally
@CanChangeDocumentDuringHighlighting
@TestApplication
public class JavaSOEOnReparseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private StringBuilder getHugeExpr() {
    int N = 100_000;
    String expr = "+\"b\"";
    return new StringBuilder(N * expr.length() + 10)
    .append("\"-\"")
    .append(StringUtil.repeat(expr, N));
  }

  @Test
  public void testOnHugeBinaryExprInFile() {
    @Language("JAVA")
    String text = "class A { String s = \"\"; }";
    daemonFixture.get().configureFromFileText("a.java", text);
    doTest();
  }

  @Test
  public void testOnHugeBinaryExprInCodeBlock() {
    @Language("JAVA")
    String text = "class A { void m() { String s = \"\"; } }";
    daemonFixture.get().configureFromFileText("a.java", text);
    doTest();
  }

  private void doTest() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    Document document = myFixture.getEditor().getDocument();
    Project project = myFixture.getProject();
    final int pos = document.getText().indexOf("\"\"");

    int hugeExprLength;
    {
      // replace small expression with huge binary one
      CharSequence myHugeExpr = getHugeExpr();
      hugeExprLength = myHugeExpr.length();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.replaceString(pos, pos + 2, myHugeExpr);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });
    }
    daemon.doTestConfiguredFile(false, false, null);

    // modify huge binary expression (1)
    myFixture.runInCommand(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.insertString(pos, "\".\"+");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }));
    // Let PSI from the previous tree leave the highlighting soft maps and reach their reference queues.
    GCUtil.tryGcSoftlyReachableObjects();
    daemon.doTestConfiguredFile(false, false, null);

    // modify huge binary expression (2)
    myFixture.runInCommand(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(pos, pos + 4, "");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }));
    daemon.doTestConfiguredFile(false, false, null);

    // replace huge binary expression with small one
    myFixture.runInCommand(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(pos, pos + hugeExprLength, "\".\"");
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }));
    daemon.doTestConfiguredFile(false, false, null);
  }
}
