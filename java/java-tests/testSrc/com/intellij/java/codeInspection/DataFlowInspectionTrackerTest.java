// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TrackingRunner;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

public class DataFlowInspectionTrackerTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/tracker/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  protected void doTest() {
    String filePath = getTestName(false) + ".java";
    PsiFile file = myFixture.configureByFile(filePath);
    SelectionModel model = myFixture.getEditor().getSelectionModel();
    int start = model.getSelectionStart();
    int end = model.getSelectionEnd();
    if (start == end) {
      fail("No selection!");
    }
    TextRange range = new TextRange(start, end);
    PsiElement element = file.findElementAt(start);
    while (element != null && element != file && 
           (!range.equals(element.getTextRange()) || element.getParent().getTextRange().equals(range))) {
      element = element.getParent();
    }
    String selectedText = model.getSelectedText();
    assertNotNull("Failed to find element at selection: " + selectedText, element);
    assertTrue("Selected element is not an expression: " + selectedText, element instanceof PsiExpression);
    PsiExpression expression = (PsiExpression)element;
    TrackingRunner.DfaProblemType problemType = getProblemType(selectedText, expression);
    TrackingRunner.CauseItem item = TrackingRunner.findProblemCause(false, expression, problemType);
    assertNotNull(item);
    String dump = item.dump(getEditor().getDocument());
    PsiComment firstComment = PsiTreeUtil.findChildOfType(file, PsiComment.class);
    if (firstComment == null) {
      fail("Comment not found");
    }
    String text = firstComment.getText();
    String prefix = StringUtil.substringBefore(text, "\n") + "\n";
    String suffix = StringUtil.substringAfterLast(text, "\n");
    String actual = prefix + dump + suffix;
    if (!text.equals(actual)) {
      PsiElement fileCopy = file.copy();
      PsiTreeUtil.findChildOfType(fileCopy, PsiComment.class)
        .replace(JavaPsiFacade.getElementFactory(getProject()).createCommentFromText(actual, null));
      int diff = actual.length() - text.length();
      String actualFile = fileCopy.getText();
      String origPath = myFixture.getTestDataPath() + "/" + filePath;
      actualFile = actualFile.substring(0, start + diff) +
                   "<selection>" +
                   actualFile.substring(start + diff, end + diff) +
                   "</selection>" +
                   actualFile.substring(end + diff);
      String expectedFile;
      try {
        expectedFile = Files.readString(Paths.get(origPath));
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      throw new FileComparisonFailure("Dump differs", expectedFile, actualFile, origPath);
    }
    assertEquals(text, actual);
  }

  @NotNull
  private static TrackingRunner.DfaProblemType getProblemType(String selectedText, PsiExpression expression) {
    if (expression instanceof PsiTypeCastExpression) {
      return new TrackingRunner.CastDfaProblemType();
    }
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiForeachStatement) {
      return new TrackingRunner.ZeroSizeDfaProblemType(
        expression.getType() instanceof PsiArrayType ? SpecialField.ARRAY_LENGTH : SpecialField.COLLECTION_SIZE);
    }
    if (parent instanceof PsiReferenceExpression) {
      // Test possible NPE in qualifiers only
      return new TrackingRunner.NullableDfaProblemType();
    }
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expression);
    if (expression instanceof PsiCallExpression && !result.cannotFailByContract((PsiCallExpression)expression)) {
      return new TrackingRunner.FailingCallDfaProblemType();
    }
    assertNotNull("No common dataflow result for expression: " + selectedText, result);
    Set<Object> values = result.getExpressionValues(expression);
    assertEquals("No single value for expression: "+selectedText, 1, values.size());
    Object singleValue = values.iterator().next();
    return new TrackingRunner.ValueDfaProblemType(singleValue);
  }

  public void testDitto() { doTest(); }
  public void testConstants() { doTest(); }
  public void testSimpleDeref() { doTest(); }
  public void testNullWasChecked() { doTest(); }
  public void testNullWasCheckedNullAssigned() { doTest(); }
  public void testNullWasCheckedInstanceOf() { doTest(); }
  public void testNotNullAnnotated() { doTest(); }
  public void testNotNullParameter() { doTest(); }
  public void testNotNullParameterAssigned() { doTest(); }
  public void testStringLength() { doTest(); }
  public void testModOutOfRange() { doTest(); }
  public void testOddityCheck() { doTest(); }
  public void testRepeatingIntegerComparison() { doTest(); }
  public void testRepeatingIntegerComparisonThreeBranches() { doTest(); }
  public void testAssignmentChain() { doTest(); }
  public void testBooleanChecked() { doTest(); }
  public void testBooleanReassignedMultipleTimes() { doTest(); }
  public void testBooleanUnderNegation() { doTest(); }
  public void testArrayLength() { doTest(); }
  public void testArrayLengthCollectionSize() { doTest(); }
  public void testInstanceOfNull() { doTest(); }
  public void testInstanceOfConflict() { doTest(); }
  public void testInstanceOfRedundant() { doTest(); }
  public void testInstanceOfChain() { doTest(); }
  public void testInstanceOfChain2() { doTest(); }
  public void testNotInstanceOf() { doTest(); }
  public void testInstanceOfPreviousCast() { doTest(); }
  public void testNotNullObvious() { doTest(); }
  public void testNotNullAssignmentInside() { doTest(); }
  public void testIndexOfPlusOne() { doTest(); }
  public void testWrongCastSimple() { doTest(); }
  public void testWrongCastTwoStates() { doTest(); }
  public void testWrongCastThreeStates() { doTest(); }
  public void testIfBothNotNull() { doTest(); }
  public void testIfBothNotNullReassign() { doTest(); }
  public void testAssignTrue() { doTest(); }
  public void testAndChainCause() { doTest(); }
  public void testAndChainDependentCause() { doTest(); }
  public void testOrChainCause() { doTest(); }
  public void testNpeSimple() { doTest(); }
  public void testNpeAnnotation() { doTest(); }
  public void testNpeWithCast() { doTest(); }
  public void testAssignTernaryNotNull() { doTest(); }
  public void testAssignTernaryNumeric() { doTest(); }
  public void testTrivialContract() { doTest(); }
  public void testTripleCheck() { doTest(); }
  public void testInstanceOfSecondCheck() { doTest(); }
  public void testNullCheckNpeNullCheck() { doTest(); }
  public void testListAddContract() { doTest(); }
  public void testConstantStrings() { doTest(); }
  public void testConstantStrings2() { doTest(); }
  public void testNumericCast() { doTest(); }
  public void testNumericCast2() { doTest(); }
  public void testNumericWidening() { doTest(); }
  public void testSimpleContract() { doTest(); }
  public void testSimpleContract2() { doTest(); }
  public void testEqualsContract() { doTest(); }
  public void testCollectionSizeEquality() { doTest(); }
  public void testFailingCall() { doTest(); }
  public void testInstanceOfMethodReturn() { doTest(); }
  public void testReassignAfterCheck() { doTest(); }
  public void testAndAllTrue() { doTest(); }
  public void testConstructorSimple() { doTest(); }
  public void testConstructorDependOnInitializer() { doTest(); }
  public void testBasedOnPreviousRelation() { doTest(); }
  public void testBasedOnPreviousRelationContracts() { doTest(); }
  public void testFinalFieldInitialized() { doTest(); }
  public void testFinalFieldInitializedCtor() { doTest(); }
  public void testEqualsNull() { doTest(); }
  public void testEnumCompare() { doTest(); }
  public void testMergeOnAnd() { doTest(); }
  public void testPassedNotNull() { doTest(); }
  public void testClassCheckInStream() { doTest(); }
  public void testEqualsLessEquals() { doTest(); }
  public void testParameterParentheses() { doTest(); }
  public void testParameterTernary() { doTest(); }
  public void testParameterTernary2() { doTest(); }
  public void testMaxParameter() { doTest(); }
  public void testReturnThis() { doTest(); }
  public void testComplexDisjunction() { doTest(); }
  public void testSubStringEqualsIgnoreCase() { doTest(); }
  public void testSubStringEqualsIgnoreCase2() { doTest(); }
  public void testArrayBlockingQueueContract() { doTest(); }
  public void testEmptyCollectionSimple() { doTest(); }
  public void testReboxing() { doTest(); }
}
