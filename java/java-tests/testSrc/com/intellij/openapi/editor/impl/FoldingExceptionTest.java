/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.CodeFoldingPassFactory;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class FoldingExceptionTest extends LightCodeInsightTestCase {

  public void test() {
    configureByFile("/codeInsight/folding/FoldingExceptionTest.java");
    EditorTestUtil.configureSoftWraps(myEditor, 120);
    runFoldingPass();
    deleteLine();
    runFoldingPass();
    // we just verify here that the operation completes normally - it was known to fail previously
  }

  private static void runFoldingPass() {
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(getProject());
    psiDocumentManager.commitAllDocuments();
    PsiFile psiFile = psiDocumentManager.getPsiFile(myEditor.getDocument());
    assertNotNull(psiFile);

    CodeFoldingPassFactory factory = getProject().getComponent(CodeFoldingPassFactory.class);
    TextEditorHighlightingPass highlightingPass = factory.createHighlightingPass(psiFile, myEditor);
    highlightingPass.collectInformation(new MockProgressIndicator());
    highlightingPass.doApplyInformationToEditor();
  }
}
