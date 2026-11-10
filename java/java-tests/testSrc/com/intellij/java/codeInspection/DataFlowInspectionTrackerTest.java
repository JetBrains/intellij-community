// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TrackingRunner;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class DataFlowInspectionTrackerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/tracker/");
  }

  protected void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String filePath = myFixture.getTestName(false) + ".java";
    PsiFile file = myFixture.configureByFile(filePath);
    ReadAction.runBlocking(() -> {
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
      assertNotNull(element, "Failed to find element at selection: " + selectedText);
      assertTrue(element instanceof PsiExpression, "Selected element is not an expression: " + selectedText);
      PsiExpression expression = (PsiExpression)element;
      TrackingRunner.DfaProblemType problemType = getProblemType(selectedText, expression);
      TrackingRunner.CauseItem item = TrackingRunner.findProblemCause(false, expression, problemType);
      assertNotNull(item);
      String dump = item.dump(myFixture.getEditor().getDocument());
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
          .replace(JavaPsiFacade.getElementFactory(myFixture.getProject()).createCommentFromText(actual, null));
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
        throw new FileComparisonFailedError("Dump differs", expectedFile, actualFile, origPath);
      }
      assertEquals(text, actual);
    });
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
    assertNotNull(result, "No common dataflow result for expression: " + selectedText);
    Set<Object> values = result.getExpressionValues(expression);
    assertEquals(1, values.size(), "No single value for expression: "+selectedText);
    Object singleValue = values.iterator().next();
    return new TrackingRunner.ValueDfaProblemType(singleValue);
  }

  @Test
  public void testDitto() {
    doTest();
  }
  @Test
  public void testConstants() {
    doTest();
  }
  @Test
  public void testSimpleDeref() {
    doTest();
  }
  @Test
  public void testNullWasChecked() {
    doTest();
  }
  @Test
  public void testNullWasCheckedNullAssigned() {
    doTest();
  }
  @Test
  public void testNullWasCheckedInstanceOf() {
    doTest();
  }
  @Test
  public void testNotNullAnnotated() {
    doTest();
  }
  @Test
  public void testNotNullParameter() {
    doTest();
  }
  @Test
  public void testNotNullParameterAssigned() {
    doTest();
  }
  @Test
  public void testStringLength() {
    doTest();
  }
  @Test
  public void testModOutOfRange() {
    doTest();
  }
  @Test
  public void testOddityCheck() {
    doTest();
  }
  @Test
  public void testRepeatingIntegerComparison() {
    doTest();
  }
  @Test
  public void testRepeatingIntegerComparisonThreeBranches() {
    doTest();
  }
  @Test
  public void testAssignmentChain() {
    doTest();
  }
  @Test
  public void testBooleanChecked() {
    doTest();
  }
  @Test
  public void testBooleanReassignedMultipleTimes() {
    doTest();
  }
  @Test
  public void testBooleanUnderNegation() {
    doTest();
  }
  @Test
  public void testArrayLength() {
    doTest();
  }
  @Test
  public void testArrayLengthCollectionSize() {
    doTest();
  }
  @Test
  public void testInstanceOfNull() {
    doTest();
  }
  @Test
  public void testInstanceOfConflict() {
    doTest();
  }
  @Test
  public void testInstanceOfRedundant() {
    doTest();
  }
  @Test
  public void testInstanceOfChain() {
    doTest();
  }
  @Test
  public void testInstanceOfChain2() {
    doTest();
  }
  @Test
  public void testNotInstanceOf() {
    doTest();
  }
  @Test
  public void testInstanceOfPreviousCast() {
    doTest();
  }
  @Test
  public void testNotNullObvious() {
    doTest();
  }
  @Test
  public void testNotNullAssignmentInside() {
    doTest();
  }
  @Test
  public void testIndexOfPlusOne() {
    doTest();
  }
  @Test
  public void testWrongCastSimple() {
    doTest();
  }
  @Test
  public void testWrongCastTwoStates() {
    doTest();
  }
  @Test
  public void testWrongCastThreeStates() {
    doTest();
  }
  @Test
  public void testIfBothNotNull() {
    doTest();
  }
  @Test
  public void testIfBothNotNullReassign() {
    doTest();
  }
  @Test
  public void testAssignTrue() {
    doTest();
  }
  @Test
  public void testAndChainCause() {
    doTest();
  }
  @Test
  public void testAndChainDependentCause() {
    doTest();
  }
  @Test
  public void testOrChainCause() {
    doTest();
  }
  @Test
  public void testNpeSimple() {
    doTest();
  }
  @Test
  public void testNpeAnnotation() {
    doTest();
  }
  @Test
  public void testNpeWithCast() {
    doTest();
  }
  @Test
  public void testAssignTernaryNotNull() {
    doTest();
  }
  @Test
  public void testAssignTernaryNumeric() {
    doTest();
  }
  @Test
  public void testTrivialContract() {
    doTest();
  }
  @Test
  public void testTripleCheck() {
    doTest();
  }
  @Test
  public void testInstanceOfSecondCheck() {
    doTest();
  }
  @Test
  public void testNullCheckNpeNullCheck() {
    doTest();
  }
  @Test
  public void testListAddContract() {
    doTest();
  }
  @Test
  public void testConstantStrings() {
    doTest();
  }
  @Test
  public void testConstantStrings2() {
    doTest();
  }
  @Test
  public void testNumericCast() {
    doTest();
  }
  @Test
  public void testNumericCast2() {
    doTest();
  }
  @Test
  public void testNumericWidening() {
    doTest();
  }
  @Test
  public void testSimpleContract() {
    doTest();
  }
  @Test
  public void testSimpleContract2() {
    doTest();
  }
  @Test
  public void testEqualsContract() {
    doTest();
  }
  @Test
  public void testCollectionSizeEquality() {
    doTest();
  }
  @Test
  public void testFailingCall() {
    doTest();
  }
  @Test
  public void testInstanceOfMethodReturn() {
    doTest();
  }
  @Test
  public void testReassignAfterCheck() {
    doTest();
  }
  @Test
  public void testAndAllTrue() {
    doTest();
  }
  @Test
  public void testConstructorSimple() {
    doTest();
  }
  @Test
  public void testConstructorDependOnInitializer() {
    doTest();
  }
  @Test
  public void testBasedOnPreviousRelation() {
    doTest();
  }
  @Test
  public void testBasedOnPreviousRelationContracts() {
    doTest();
  }
  @Test
  public void testFinalFieldInitialized() {
    doTest();
  }
  @Test
  public void testFinalFieldInitializedCtor() {
    doTest();
  }
  @Test
  public void testEqualsNull() {
    doTest();
  }
  @Test
  public void testEnumCompare() {
    doTest();
  }
  @Test
  public void testMergeOnAnd() {
    doTest();
  }
  @Test
  public void testPassedNotNull() {
    doTest();
  }
  @Test
  public void testClassCheckInStream() {
    doTest();
  }
  @Test
  public void testEqualsLessEquals() {
    doTest();
  }
  @Test
  public void testParameterParentheses() {
    doTest();
  }
  @Test
  public void testParameterTernary() {
    doTest();
  }
  @Test
  public void testParameterTernary2() {
    doTest();
  }
  @Test
  public void testMaxParameter() {
    doTest();
  }
  @Test
  public void testReturnThis() {
    doTest();
  }
  @Test
  public void testComplexDisjunction() {
    doTest();
  }
  @Test
  public void testSubStringEqualsIgnoreCase() {
    doTest();
  }
  @Test
  public void testSubStringEqualsIgnoreCase2() {
    doTest();
  }
  @Test
  public void testArrayBlockingQueueContract() {
    doTest();
  }
  @Test
  public void testEmptyCollectionSimple() {
    doTest();
  }
  @Test
  public void testReboxing() {
    doTest();
  }
}
