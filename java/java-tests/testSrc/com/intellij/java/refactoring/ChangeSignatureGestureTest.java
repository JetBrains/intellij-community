// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

@HeavyPlatformTestCase.WrapInCommand
public class ChangeSignatureGestureTest extends LightJavaCodeInsightFixtureTestCase {

  private void doTest(final Runnable run) {
    myFixture.configureByFile("refactoring/changeSignatureGesture/" + getTestName(false) + ".java");
    myFixture.enableInspections(new UnusedDeclarationInspection());
    CommandProcessor.getInstance().executeCommand(
      myFixture.getProject(), () ->
        new InplaceChangeSignature(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile().findElementAt(myFixture.getCaretOffset())),
      ChangeSignatureHandler.REFACTORING_NAME, null);
    run.run();

    IntentionAction action = myFixture.findSingleIntention("Changing signature of ");
    myFixture.launchAction(action);
    myFixture.checkResultByFile("refactoring/changeSignatureGesture/" + getTestName(false) + "_after.java");
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
      assertNotNull(method);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      assertNotNull(returnTypeElement);
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
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doDeleteTest());
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