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

/**
 * @author cdr
 */
package com.intellij.java.codeInsight.daemon;

import com.intellij.java.codeInsight.defaultAction.DefaultActionTestCase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;

public class OutOfCodeBlockModificationTest extends DefaultActionTestCase {

  public void testInsideBlock() {
    doTest("class a{ void f(){int i;<caret> int j;}}", true);
  }
  public void testInsideBlock2() {
    doTest("class a{ void f(){ <caret>}}", true);
  }
  public void testOutsideBlock() {
    doTest("class a{ <caret> void f(){}}", false);
  }

  private void doTest(String fileText, boolean inside) {
    configureFromFileText("a.java",fileText);
    PsiFile file = getFile();
    PsiManager manager = file.getManager();
    PsiModificationTracker modificationTracker = manager.getModificationTracker();
    long codeBlockModificationCount = modificationTracker.getOutOfCodeBlockModificationCount();
    performAction('n');
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    documentManager.commitAllDocuments();
    long afterCodeBlockModificationCount = modificationTracker.getOutOfCodeBlockModificationCount();
    assertTrue("Out of code block modification " + (inside ? "" : "not") + " detected",
               codeBlockModificationCount == afterCodeBlockModificationCount == inside);
  }
}