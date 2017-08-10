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
package com.intellij.java.psi;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.SkipSlowTestLocally;

@SkipSlowTestLocally
public class JavaSOEOnReparsePerformanceTest extends LightDaemonAnalyzerTestCase {
  private StringBuilder myHugeExpr;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHugeExpr = new StringBuilder("\"-\"");
    for (int i = 0; i < 100000; i++) myHugeExpr.append("+\"b\"");
  }

  @Override
  public void tearDown() throws Exception {
    if (myHugeExpr != null) {
      myHugeExpr.setLength(0);
      myHugeExpr = null;
    }
    super.tearDown();
  }

  public void testOnHugeBinaryExprInFile() {
    configureFromFileText("a.java", "class A { String s = \"\"; }");
    doTest();
  }

  public void testOnHugeBinaryExprInCodeBlock() {
    configureFromFileText("a.java", "class A { void m() { String s = \"\"; } }");
    doTest();
  }

  private void doTest() {
    final int pos = getEditor().getDocument().getText().indexOf("\"\"");

    // replace small expression with huge binary one
    WriteCommandAction.runWriteCommandAction(null, () -> {
      getEditor().getDocument().replaceString(pos, pos + 2, myHugeExpr);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    doTestConfiguredFile(false, false, null);

    // modify huge binary expression (1)
    ApplicationManager.getApplication().runWriteAction(() -> {
      getEditor().getDocument().insertString(pos, "\".\"+");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    doTestConfiguredFile(false, false, null);

    // modify huge binary expression (2)
    ApplicationManager.getApplication().runWriteAction(() -> {
      getEditor().getDocument().replaceString(pos, pos + 4, "");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    doTestConfiguredFile(false, false, null);

    // replace huge binary expression with small one
    ApplicationManager.getApplication().runWriteAction(() -> {
      getEditor().getDocument().replaceString(pos, pos + myHugeExpr.length(), "\".\"");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    doTestConfiguredFile(false, false, null);
  }
}
