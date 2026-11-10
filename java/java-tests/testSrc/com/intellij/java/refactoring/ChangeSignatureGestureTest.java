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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inplace.InplaceChangeSignature;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@HeavyPlatformTestCase.WrapInCommand
@TestApplication
public class ChangeSignatureGestureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  private void doTest(final Runnable run) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("refactoring/changeSignatureGesture/" + myFixture.getTestName(false) + ".java");
    myFixture.enableInspections(new UnusedDeclarationInspection());
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(
        myFixture.getProject(), () ->
          new InplaceChangeSignature(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile().findElementAt(myFixture.getCaretOffset())),
        RefactoringBundle.message("changeSignature.refactoring.name"), null);
      run.run();

      IntentionAction action = myFixture.findSingleIntention("Changing signature of ");
      myFixture.launchAction(action);
      myFixture.checkResultByFile("refactoring/changeSignatureGesture/" + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testSimple() {
    doTypingTest(", int param");
  }

  @Test
  public void testNoUsages() {
    doTypingTest("int param");
  }

  @Test
  public void testOccurrencesInSameFile() {
    doTypingTest("int param");
  }
  
  @Test
  public void testMultiParams() {
    doTypingTest("int x, int y");
  }

  @Test
  public void testAddParameter2Constructor() {
    doTypingTest("int param");
  }

  @Test
  public void testAddParamChangeReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      myFixture.type("int param");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
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

  @Test
  public void testNewParam() {
    doTypingTest(", int param");
  }

  @Test
  public void testNewParamInSuper() {
    doTypingTest(", int param");
  }

  @Test
  public void testNewParamInSuperUsed() {
    doTypingTest(", int param");
  }

  private void doTypingTest(final String param) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> myFixture.type(param));
  }

  @Test
  public void testModifier() {
    doTypingTest("private ");
  }

  @Test
  public void testAddParameterFinal() {
    doTypingTest("final int param");
  }

  @Test
  public void testDeleteParamInSuperUsed() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doDeleteTest());
  }

  private void doDeleteTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(() -> {
      final Editor editor = myFixture.getEditor();
      final Document document = editor.getDocument();
      final int selectionStart = editor.getSelectionModel().getSelectionStart();
      final int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.deleteString(selectionStart, selectionEnd));
      editor.getCaretModel().moveToOffset(selectionStart);
    });
  }
}