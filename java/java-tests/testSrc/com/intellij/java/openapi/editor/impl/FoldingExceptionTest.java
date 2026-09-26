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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

public class FoldingExceptionTest extends LightJavaCodeInsightTestCase {
  public void test() {
    doTest("FoldingExceptionTest.java");
  }

  public void testAnother() {
    doTest("FoldingException2Test.java");
  }

  private void doTest(String fileName) {
    configureByFile("/codeInsight/folding/" + fileName);
    EditorTestUtil.configureSoftWraps(getEditor(), 120);
    runFoldingPass();
    deleteLine();
    runFoldingPass();
    // we just verify here that the operation completes normally - it was known to fail previously
  }

  private void runFoldingPass() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    CodeInsightTestFixtureImpl.instantiateAndRun(getFile(), getEditor(), new int[]{Pass.UPDATE_ALL, Pass.LOCAL_INSPECTIONS}, false);
  }
}
