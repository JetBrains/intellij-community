/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExtractMethodTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/extractMethod/";
  private boolean myCatchOnNewLine = true;

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testExitPoints1() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints2() throws Exception {
    doTest();
  }

  public void testExitPoints3() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints4() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPointsInsideLoop() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints5() throws Exception {
    doTest();
  }

  public void testExitPoints6() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints7() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints8() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints9() throws Exception {
    doExitPointsTest(false);
  }

  public void testContinueInside() throws Exception {
    doTest();
  }

  public void testBooleanExpression() throws Exception {
    doTest();
  }

  public void testScr6241() throws Exception {
    doTest();
  }

  public void testScr7091() throws Exception {
    doTest();
  }

  public void testScr10464() throws Exception {
    doTest();
  }

  public void testScr9852() throws Exception {
    doTest();
  }

  public void testUseVarAfterTry() throws Exception {
    doTest();
  }

  public void testOneBranchAssignment() throws Exception {
    doTest();
  }

  public void testExtractFromCodeBlock() throws Exception {
    doTest();
  }

  public void testUnusedInitializedVar() throws Exception {
    doTest();
  }

  public void testTryFinally() throws Exception {
    doTest();
  }

  public void testFinally() throws Exception {
    doTest();
  }

  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  public void testSCR12245() throws Exception {
    doTest();
  }

  public void testLeaveCommentsWhenExpressionExtracted() throws Exception {
    doTest();
  }

  public void testSCR15815() throws Exception {
    doTest();
  }

  public void testSCR27887() throws Exception {
    doTest();
  }

  public void testSCR28427() throws Exception {
    doTest();
  }

  public void testTryFinallyInsideFor() throws Exception {
    doTest();
  }

  public void testExtractFromTryFinally() throws Exception {
    doTest();
  }

  public void testExtractAssignmentExpression() throws Exception {
    doTest();
  }

  public void testExtractAssignmentExpressionFromStatement() throws Exception {
    doTest();
  }

  public void _testExtractFromTryFinally2() throws Exception {  // IDEADEV-11844
    doTest();
  }

  public void testLesyaBug() throws Exception {
    myCatchOnNewLine = false;
    doTest();
  }

  public void testForEach() throws Exception {
    doTest();
  }

  public void testAnonInner() throws Exception {
    doTest();
  }


  public void testConflictingAnonymous() throws Exception {
    doTest();
  }

  public void testVarDeclAfterExpressionExtraction() throws Exception {
    doTest();
  }

  public void testFinalParamUsedInsideAnon() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_PARAMETERS = false;
    doTest();
  }

  public void testNonFinalWritableParam() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_PARAMETERS = true;
    doTest();
  }

  public void testCodeDuplicatesWithContinue() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithContinueNoReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithStaticInitializer() throws Exception {
    doDuplicatesTest();
  }

  public void testExpressionDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates3() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates4() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates5() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue1() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithMultExitPoints() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturnInAnonymous() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithComments() throws Exception {
    doDuplicatesTest();
  }

  public void testSCR32924() throws Exception {
    doDuplicatesTest();
  }

  public void testFinalOutputVar() throws Exception {
    doDuplicatesTest();
  }

  public void testIdeaDev2291() throws Exception {
    doTest();
  }

  public void testOxfordBug() throws Exception {
    doTest();
  }

  public void testIDEADEV33368() throws Exception {
    doTest();
  }

  public void testInlineCreated2ReturnLocalVariablesOnly() throws Exception {
    doTest();
  }

  public void testGuardMethodDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testGuardMethodDuplicates1() throws Exception {
    doDuplicatesTest();
  }

  public void testInstanceMethodDuplicatesInStaticContext() throws Exception {
    doDuplicatesTest();
  }


  public void testLValueNotDuplicate() throws Exception {
    doDuplicatesTest();
  }

  protected void doDuplicatesTest() throws Exception {
    doTest(true);
  }

  public void testExtractFromFinally() throws Exception {
    doTest();
  }

  public void testNoShortCircuit() throws Exception {
    doTest();
  }

  public void testStopFolding() throws Exception {
    doTest();
  }

  public void testStopFoldingPostfixInside() throws Exception {
    doTest();
  }


  public void testIDEADEV11748() throws Exception {
    doTest();
  }

  public void testIDEADEV11848() throws Exception {
    doTest();
  }

  public void testIDEADEV11036() throws Exception {
    doTest();
  }

  public void testLocalClass() throws Exception {
    doPrepareErrorTest("Cannot extract method because the selected code fragment uses local classes defined outside of the fragment");
  }

  public void testLocalClassUsage() throws Exception {
    doPrepareErrorTest("Cannot extract method because the selected code fragment defines local classes used outside of the fragment");
  }

  public void testStaticImport() throws Exception {
    doTest();
  }

  public void testThisCall() throws Exception {
    doTest();
  }

  public void testChainedConstructor() throws Exception {
    doChainedConstructorTest(false);
  }

  public void testChainedConstructorDuplicates() throws Exception {
    doChainedConstructorTest(true);
  }

  public void testChainedConstructorInvalidDuplicates() throws Exception {
    doChainedConstructorTest(true);
  }

  public void testReturnFromTry() throws Exception {
    doTest();
  }

  public void testLocalClassDefinedInMethodWhichIsUsedLater() throws Exception {
    doPrepareErrorTest("Cannot extract method because the selected code fragment defines variable of local class type used outside of the fragment");
  }

  public void testForceBraces() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    int old = settings.IF_BRACE_FORCE;
    settings.IF_BRACE_FORCE = CodeStyleSettings.FORCE_BRACES_ALWAYS;
    try {
      doTest();
    }
    finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  public void testConstantConditionsAffectingControlFlow() throws Exception {
    doTest();
  }

  public void testConstantConditionsAffectingControlFlow1() throws Exception {
    doTest();
  }
  public void testNotInitializedInsideFinally() throws Exception {
    doTest();
  }

  public void testGenericsParameters() throws Exception {
    doTest();
  }

  public void testUnusedGenerics() throws Exception {
    doTest();
  }

  public void testParamsUsedInLocalClass() throws Exception {
    doTest();
  }

  private void doChainedConstructorTest(final boolean replaceAllDuplicates) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, replaceAllDuplicates, getEditor(), getFile(), getProject(), true);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testReassignedVarAfterCall() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    boolean oldGenerateFinalLocals = settings.GENERATE_FINAL_LOCALS;
    try {
      settings.GENERATE_FINAL_LOCALS = true;
      doTest();
    }
    finally {
      settings.GENERATE_FINAL_LOCALS = oldGenerateFinalLocals;
    }
  }

  public void testNullableCheck() throws Exception {
    doTest();
  }
  
  public void testNullableCheck1() throws Exception {
    doTest();
  }

  public void testNullableCheckVoid() throws Exception {
    doTest();
  }

  public void testNullableCheckDontMissFinal() throws Exception {
    doTest();
  }

  public void testSimpleArrayAccess() throws Exception {
    doTest();
  }

  public void testArrayAccess() throws Exception {
    doTest();
  }

  public void testArrayAccess1() throws Exception {
    doTest();
  }

  public void testArrayAccessWithLocalIndex() throws Exception {
    doTest();
  }

  public void testArrayAccessWithDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testVerboseArrayAccess() throws Exception {
    doTest();
  }

  public void testReturnStatementFolding() throws Exception {
    doTest();
  }

  public void testWriteArrayAccess() throws Exception {
    doTest();
  }

  public void testShortCircuit() throws Exception {
    doTest();
  }

  public void testRecursiveCallToExtracted() throws Exception {
    doTest();
  }

  public void testCodeDuplicatesVarargsShouldNotChangeReturnType() throws Exception {
    doDuplicatesTest();
  }

  public void testParametersFromAnonymous() throws Exception {
    doTest();
  }

  public void testCast4ParamGeneration() throws Exception {
    doTest();
  }

  public void testNearComment() throws Exception {
    doTest();
  }

  public void testFoldInWhile() throws Exception {
    doTest();
  }

  public void testNonFoldInIfBody() throws Exception {
    doTest();
  }

  public void testComplexTypeParams() throws Exception {
    doTest();
  }

  public void testExtractWithLeadingComment() throws Exception {
    doTest();
  }

  public void testInvalidReference() throws Exception {
    doTest();
  }

  public void testRedundantCast() throws Exception {
    doTest();
  }
  
  public void testDisabledParam() throws Exception {
    doTestDisabledParam();
  } 

  public void testTypeParamsList() throws Exception {
    doTest();
  }

  public void testFromLambdaBody() throws Exception {
    doTest();
  }

  public void testFromLambdaBody1() throws Exception {
    doTest();
  }

  public void testOneLineLambda() throws Exception {
    doTest();
  }

  public void testMethod2Interface() throws Exception {
    doTest();
  }

  public void testParamDetection() throws Exception {
    doTest();
  }

  public void testSkipComments() throws Exception {
    doTest();
  }

  public void testFinalParams4LocalClasses() throws Exception {
    doTest();
  }

  public void testTwoFromThreeEqStatements() throws Exception {
    doDuplicatesTest();
  }

  public void testCastWhenDuplicateReplacement() throws Exception {
    doDuplicatesTest();
  }

  public void testCheckQualifierMapping() throws Exception {
    doDuplicatesTest();
  }

  public void testArrayReturnType() throws Exception {
    doDuplicatesTest();
  }

  public void testOverloadedMethods() throws Exception {
    doDuplicatesTest();
  }

  public void testTargetAnonymous() throws Exception {
    doTest();
  }

  public void testExpression() throws Exception {
    doTest();
  }

  private void doTestDisabledParam() throws PrepareFailedException {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, 0);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void doPrepareErrorTest(final String expectedMessage) throws Exception {
    String expectedError = null;
    try {
      doExitPointsTest(false);
    }
    catch(PrepareFailedException ex) {
      expectedError = ex.getMessage();
    }
    assertEquals(expectedMessage, expectedError);
  }

  private void doExitPointsTest(boolean shouldSucceed) throws Exception {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    boolean success = performAction(false, false);
    assertEquals(shouldSucceed, success);
  }

  void doTest() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    doTest(true);
  }

  private void doTest(boolean duplicates) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performAction(true, duplicates);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private static boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) throws Exception {
    return performExtractMethod(doRefactor, replaceAllDuplicates, getEditor(), getFile(), getProject());
  }

  public static boolean performExtractMethod(boolean doRefactor, boolean replaceAllDuplicates, Editor editor, PsiFile file, Project project)
    throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, false);
  }

  public static boolean performExtractMethod(boolean doRefactor, boolean replaceAllDuplicates, Editor editor, PsiFile file, Project project,
                                             final boolean extractChainedConstructor)
    throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor, null);
  }

  public static boolean performExtractMethod(boolean doRefactor,
                                             boolean replaceAllDuplicates,
                                             Editor editor,
                                             PsiFile file,
                                             Project project,
                                             final boolean extractChainedConstructor,
                                             int... disabledParams)
    throws PrepareFailedException, IncorrectOperationException {
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    if (elements.length == 0) {
      final PsiExpression expression = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
      if (expression != null) {
        elements = new PsiElement[]{expression};
      }
    }
    assertTrue(elements.length > 0);

    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, "Extract Method", "newMethod", null);
    processor.setShowErrorDialogs(false);
    processor.setChainedConstructor(extractChainedConstructor);

    if (!processor.prepare()) {
      return false;
    }

    if (doRefactor) {
      processor.testPrepare();
      if (disabledParams != null) {
        for (int param : disabledParams) {
          processor.doNotPassParameter(param);
        }
      }
      ExtractMethodHandler.run(project, editor, processor);
    }

    if (replaceAllDuplicates) {
      final List<Match> duplicates = processor.getDuplicates();
      for (final Match match : duplicates) {
        if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        processor.processMatch(match);
      }
    }

    return true;
  }
}
