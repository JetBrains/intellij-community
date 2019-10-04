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

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.SkipSlowTestLocally;
import org.intellij.lang.annotations.Language;

@SkipSlowTestLocally
public class JavaSOEOnReparsePerformanceTest extends LightDaemonAnalyzerTestCase {
  private StringBuilder myHugeExpr;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    int N = 100_000;
    myHugeExpr = new StringBuilder(N * 4 + 10);
    myHugeExpr.append("\"-\"");
    myHugeExpr.append(StringUtil.repeat("+\"b\"", N));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myHugeExpr != null) {
        myHugeExpr.setLength(0);
        myHugeExpr = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testOnHugeBinaryExprInFile() {
    @Language("JAVA")
    String text = "class A { String s = \"\"; }";
    configureFromFileText("a.java", text);
    doTest();
  }

  public void testOnHugeBinaryExprInCodeBlock() {
    @Language("JAVA")
    String text = "class A { void m() { String s = \"\"; } }";
    configureFromFileText("a.java", text);
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
