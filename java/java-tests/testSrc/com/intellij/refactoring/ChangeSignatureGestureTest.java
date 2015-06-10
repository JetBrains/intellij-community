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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureDetectorAction;
import com.intellij.refactoring.changeSignature.ChangeSignatureGestureDetector;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

/**
 * User: anna
 * Date: Sep 9, 2010
 */
public class ChangeSignatureGestureTest extends LightCodeInsightFixtureTestCase {

  private void doTest(final Runnable run, boolean shouldShow, final String hint) {
    myFixture.configureByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + ".java");
    myFixture.enableInspections(new UnusedDeclarationInspection());
    final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(getProject());
    final EditorEx editor = (EditorEx)myFixture.getEditor();
    final Document document = editor.getDocument();
    try {
      PsiManager.getInstance(getProject()).addPsiTreeChangeListener(detector);
      detector.addDocListener(document);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          run.run();
        }
      }.execute().throwException();


      myFixture.doHighlighting();
      if (shouldShow) {
        final IntentionAction intention = myFixture.findSingleIntention(hint);
        myFixture.launchAction(intention);
        myFixture.checkResultByFile("/refactoring/changeSignatureGesture/" + getTestName(false) + "_after.java");
      }
      else {
        final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(hint);
        assertEmpty(intentionActions);
      }
    }
    finally {
      detector.removeDocListener(document, editor.getVirtualFile());
      PsiManager.getInstance(getProject()).removePsiTreeChangeListener(detector);
    }
  }

  public void testSimple() {
    doTypingTest("param");
  }

  public void testSpaces() {
    doTypingNoBorderTest("   ");
  }

  public void testNoUsages() {
    doTypingNoBorderTest("int param");
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

  public void testAddParameter2UnusedConstructor() {
    doTypingNoBorderTest("int param");
  }

  public void testOnAnotherMethod() {
    doTest(() -> {
      myFixture.type("int param");
      final int nextMethodOffset = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[1].getTextOffset();
      myFixture.getEditor().getCaretModel().moveToOffset(nextMethodOffset);
    }, false, ChangeSignatureDetectorAction.CHANGE_SIGNATURE);
  }
  
  public void testAddParamChangeReturnType() {
    doTest(() -> {
      myFixture.type("int param");
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
    }, true, ChangeSignatureDetectorAction.CHANGE_SIGNATURE);
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

  public void testRenameLocalVariable() {
    doTypingTest("1", ChangeSignatureDetectorAction.NEW_NAME);
  }

  private void doTypingTest(final String param) {
    doTypingTest(param, ChangeSignatureDetectorAction.CHANGE_SIGNATURE);
  }

  private void doTypingTest(final String param, final String hint) {
    doTest(() -> myFixture.type(param), true, hint);
  }

  public void testReturnValue() {
    doTypingNoBorderTest("void");
  }

  public void testModifier() {
    doTypingNoBorderTest("private");
  }

  public void testAddParameterFinal() {
    doTypingTest("final int param");
  }

  private void doTypingNoBorderTest(final String param) {
    doTest(() -> myFixture.type(param), false, ChangeSignatureDetectorAction.CHANGE_SIGNATURE);
  }

  public void testDeleteParamInSuperUsed() {
    doDeleteTest();
  }

  private void doDeleteTest() {
    doTest(() -> {
      final Editor editor = myFixture.getEditor();
      final Document document = editor.getDocument();
      final int selectionStart = editor.getSelectionModel().getSelectionStart();
      final int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      document.deleteString(selectionStart, selectionEnd);
      editor.getCaretModel().moveToOffset(selectionStart);
    }, true, ChangeSignatureDetectorAction.CHANGE_SIGNATURE);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }
}
