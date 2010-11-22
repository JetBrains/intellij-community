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
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightCodeInsightTestCase;


public class JavaSOEOnReparseTest extends LightCodeInsightTestCase {
  private static final String HUGE_EXPR;
  static {
    final StringBuilder sb = new StringBuilder("\"-\"");
    for (int i = 0; i < 10000; i++) sb.append("+\"b\"");
    HUGE_EXPR = sb.toString();
  }

  public void testOnHugeBinaryExprInFile() throws Exception {
    configureFromFileText("a.java", "class A { String s = \"\"; }");
    doTest();
  }

  public void testOnHugeBinaryExprInCodeBlock() throws Exception {
    configureFromFileText("a.java", "class A { void m() { String s = \"\"; } }");
    doTest();
  }

  private static void doTest() {
    final int pos = getEditor().getDocument().getText().indexOf("\"\"");

    // replace small expression with huge binary one
    ApplicationManager.getApplication().runWriteAction(new Runnable() { public void run() {
      getEditor().getDocument().replaceString(pos, pos + 2, HUGE_EXPR);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }});

    // modify huge binary expression (1)
    ApplicationManager.getApplication().runWriteAction(new Runnable() { public void run() {
      getEditor().getDocument().insertString(pos, "\".\"+");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }});

    // modify huge binary expression (2)
    ApplicationManager.getApplication().runWriteAction(new Runnable() { public void run() {
      getEditor().getDocument().replaceString(pos, pos + 4, "");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }});

    // replace huge binary expression with small one
    ApplicationManager.getApplication().runWriteAction(new Runnable() { public void run() {
      getEditor().getDocument().replaceString(pos, pos + HUGE_EXPR.length(), "\".\"");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }});
  }
}
