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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.changeSignature.inplace.InplaceChangeSignature;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

@PlatformTestCase.WrapInCommand
public class ChangeSignatureGestureTest extends LightCodeInsightFixtureTestCase {

  private void doTest(final Runnable run) {
    myFixture.configureByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + ".java");
    myFixture.enableInspections(new UnusedDeclarationInspection());
    CommandProcessor.getInstance().executeCommand(
      myFixture.getProject(), () ->
        new InplaceChangeSignature(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile().findElementAt(myFixture.getCaretOffset())),
      ChangeSignatureHandler.REFACTORING_NAME, null);
    run.run();

    IntentionAction action = myFixture.findSingleIntention("Changing signature of ");
    myFixture.launchAction(action);
    myFixture.checkResultByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + "_after.java");
  }

  public void testSimple() {
    doTypingTest(", int param");
  }


  public void testNoUsages() {
    doTypingTest("int param");
  }

  public void testOccurrencesInSameFile() {
    doTypingTest("int param");
  }
  
  public void testMultiParams() {
    doTypingTest("int x, int y");
  }

  public void testAddParameter2Constructor() {
    doTypingTest("int param");
  }

  public void testAddParamChangeReturnType() {
    doTest(() -> {
      myFixture.type("int param");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      CaretModel model = myFixture.getEditor().getCaretModel();
      PsiElement element = myFixture.getElementAtCaret();
      PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
      assertTrue(method != null);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      assertTrue(returnTypeElement != null);
      model.moveToOffset(returnTypeElement.getTextRange().getEndOffset());
      int i = returnTypeElement.getTextLength();
      while (i-- > 0) {
        myFixture.type('\b');
      }
      myFixture.type("boolean");
    });
  }

  public void testNewParam() {
    doTypingTest(", int param");
  }

  public void testNewParamInSuper() {
    doTypingTest(", int param");
  }

  public void testNewParamInSuperUsed() {
    doTypingTest(", int param");
  }

  private void doTypingTest(final String param) {
    doTest(() -> myFixture.type(param));
  }

  public void testModifier() {
    doTypingTest("private ");
  }

  public void testAddParameterFinal() {
    doTypingTest("final int param");
  }

  public void testDeleteParamInSuperUsed() {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doDeleteTest();
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  private void doDeleteTest() {
    doTest(() -> {
      final Editor editor = myFixture.getEditor();
      final Document document = editor.getDocument();
      final int selectionStart = editor.getSelectionModel().getSelectionStart();
      final int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(selectionStart, selectionEnd));
      editor.getCaretModel().moveToOffset(selectionStart);
    });
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }
}
