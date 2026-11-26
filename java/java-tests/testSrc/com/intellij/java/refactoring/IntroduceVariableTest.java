// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class IntroduceVariableTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData");

  @Test
  public void testSimpleExpression() { doTest("i", false, false, true, "int"); }
  @Test
  public void testInsideFor() { doTest("temp", false, false, true, "int"); }
  @Test
  public void testReplaceAll() {
    doTestReplaceAll("s", true, true, true, JAVA_LANG_STRING);
  }

  @Test
  public void testIDEADEV3678() {
    doTestReplaceAll("component", true, true, true, CommonClassNames.JAVA_LANG_OBJECT);
  }
  @Test
  public void testIDEADEV13369() { doTest("ints", true, true, true, "int[]"); }
  @Test
  public void testAnonymousClass() {
    doTestReplaceAll("temp", true, false, true, "int");
  }
  @Test
  public void testAnonymousClass1() { doTest("runnable", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE); }
  @Test
  public void testAnonymousClass2() {
    doTestReplaceAll("j", true, false, false, "int");
  }
  @Test
  public void testAnonymousClass3() {
    doTestReplaceAll("j", true, false, false, "Foo");
  }
  @Test
  public void testAnonymousClass4() { doTest("j", true, false, false, "int"); }
  @Test
  public void testAnonymousClass5() { doTest("j", true, false, false, "int"); }
  @Test
  public void testAnonymousClass6() {
    doTestReplaceAll("str", true, false, false, JAVA_LANG_STRING);
  }
  @Test
  public void testAnonymousClass7() { doTest("str", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testLambda() { doTest("j", true, false, false, "int"); }
  @Test
  public void testParenthesized() { doTest("temp", true, false, false, "int"); }
  @Test
  public void testExpectedType8Inference() { doTest("temp", true, false, false, "java.util.Map<java.lang.String,java.util.List<java.lang.String>>"); }
  @Test
  public void testMethodCall() {
    doTestReplaceAll("temp", true, true, true, CommonClassNames.JAVA_LANG_OBJECT);
  }
  @Test
  public void testMethodCallInSwitch() {
    doTestReplaceAll("i", true, true, true, "int");
  }
  @Test
  public void testFunctionalExpressionInSwitch() { doTest("p", true, true, true, "java.util.function.Predicate<java.lang.String>"); }
  @Test
  public void testParenthesizedOccurrence1() {
    doTestReplaceAll("empty", true, true, true, "boolean");
  }
  @Test
  public void testParenthesizedOccurrence2() { doTest("s", true, true, true, JAVA_LANG_STRING); }
  @Test
  public void testAfterSemicolon() {
    UiInterceptors.register(new ChooserInterceptor(null, StringUtil.escapeToRegexp("new Runnable() {...}")));
    doTest("s", true, true, true, CommonClassNames.JAVA_LANG_RUNNABLE);
  }
  @Test
  public void testConflictingField() { doTest("name", true, false, true, JAVA_LANG_STRING); }
  @Test
  public void testConflictingFieldInExpression() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest("name", false, false, true, "int");
  }
  @Test
  public void testStaticConflictingField() { doTest("name", false, false, true, "int"); }
  @Test
  public void testNonConflictingField() { doTest("name", false, false, true, "int"); }
  @Test
  public void testScr16910() { doTest("i", true, true, false, "int"); }
  @Test
  public void testSCR18295() { doTest("it", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSCR18295a() { doTest("it", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testFromInjected() { doTest("regexp", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSCR10412() { doTest("newVar", false, false, false, "java.lang.String[]"); }
  @Test
  public void testSCR22718() {
    doTestReplaceAll("object", true, true, false, CommonClassNames.JAVA_LANG_OBJECT);
  }
  @Test
  public void testSCR26075() { doTest("ok", false, false, false, JAVA_LANG_STRING); }

  @Test
  public void testSCR26075For() {
    doTest(new MockIntroduceVariableHandler("wrong", false, false, false, JAVA_LANG_STRING) {
      @Override
      protected void assertValidationResult(boolean validationResult) {
        assertFalse(validationResult);
      }

      @Override
      protected void showErrorMessage(@NotNull Project project, Editor editor, @NotNull String message) {
        assertEquals("Introducing variable may break code logic<br>Local variable <b><code>c</code></b> is modified in loop body", message);
      }
    });
  }

  @Test
  public void testConflictingFieldInOuterClass() { doTest("text", true, true, false, JAVA_LANG_STRING); }
  @Test
  public void testSkipSemicolon() { doTest("mi5", false, false, false, "int"); }
  @Test
  public void testSkipErroneousParen() { doTest("x", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testInsideIf() { doTest("s1", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testInsideElse() { doTest("s1", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testInsideWhile() { doTest("temp", false, false, false, "int"); }

  @Test
  public void testWhileCondition() {
    doTestReplaceAll("temp", true, false, false, "Node");
  }

  @Test
  public void testWhileCondition2() {
    doTestReplaceAll("temp", true, false, false, "Node");
  }

  @Test
  public void testWhileConditionIncomplete() { doTest("temp", true, false, false, "boolean"); }

  @Test
  public void testWhileConditionNoBrace() { doTest("temp", true, false, false, "int"); }

  @Test
  public void testWhileConditionPlusNormal() {
    doTestReplaceAll("temp", true, false, false, "int");
  }

  @Test
  public void testWhileConditionPlusNormal2() {
    doTestReplaceAll("temp", true, false, false, "int");
  }

  @Test
  public void testWhileConditionAndOr() { doTest("temp", true, false, false, "boolean"); }

  @Test
  public void testField() {
    doTestReplaceAll("temp", false, false, false, JAVA_LANG_STRING);
  }

  @Test
  public void testFieldAll() {
    doTestReplaceAll("temp", true, false, false, JAVA_LANG_STRING);
  }

  @Test
  public void testCaseLabel() {
    doTestReplaceAll("temp", true, false, false, "int");
  }
  @Test
  public void testCaseLabelSingle() { doTest(new IntroduceVariableHandler()); }
  @Test
  public void testCaseLabelRuleSingle() { doTest(new IntroduceVariableHandler()); }
  @Test
  public void testCaseLabelRuleExpression() { doTest(new IntroduceVariableHandler()); }

  @Test
  public void testCaseLabelEnum() {
    doTestWithFailure("temp", "", JavaRefactoringBundle.message("refactoring.introduce.variable.enum.in.label.message"));
  }

  @Test
  public void testPatternVariableUsedAfterwards() {
    doTestWithFailure("temp", "", JavaRefactoringBundle.message("selected.expression.introduces.pattern.variable", "s"));
  }
  @Test
  public void testPatternVariableNotUsedAfterwards() {
    doTest("temp", true, false, false, "boolean");
  }
  @Test
  public void testPatternVariableDeclarationJava16() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, JAVA_LANG_STRING));
  }
  @Test
  public void testPatternVariableDeclarationUpcastJava16() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, JAVA_LANG_STRING));
  }
  @Test
  public void testPatternVariableDeclarationUsedInLocalJava16() { doTest("temp", true, false, false, JAVA_LANG_STRING);}
  @Test
  public void testPatternVariableDeclarationAfterIfJava16() { doTestReplaceAll("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testNonPatternVariableDeclarationTwoBlocksJava16() { doTestReplaceAll("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testNonPatternDeclarationJava16() { doTest("temp", true, false, false, JAVA_LANG_STRING);}

  @Test
  public void testTernaryBothBranches() { doTestReplaceAll("temp", true, false, false, "int"); }
  @Test
  public void testIfConditionAndChain() {
  LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> doTest("temp", true, false, false, JAVA_LANG_STRING)); }
  @Test
  public void testReturnAndChain() {
  LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> doTest("temp", true, false, false, JAVA_LANG_STRING)); }
  @Test
  public void testReturnOrChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testReturnOrAndChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testReturnTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testFieldInitializer() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testFieldInitializerDenormalized() { doTest("temp", true, false, false, "int"); }
  @Test
  public void testAssignTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testEnsureCodeBlockAroundBreakStatement() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testEnsureCodeBlockForThrows() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testFromSwitchStatement() { doTest("temp", true, false, false, JAVA_LANG_STRING); }

  @Test
  public void testVarTypeExtractedJava10() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "java.util.ArrayList<java.lang.String>"));
  }

  @Test
  public void testVarTypeArrayExtractedJava10() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "int[]"));
  }

  @Test
  public void testVarTypeLambdaTypeCast() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "I"));
  }

  @Test
  public void testTypeContainingVarJava11() {
    doTest("temp", true, false, false, "var.X");
  }

  @Test
  public void testDeclareTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testLambdaAndChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSCR40281() { doTest("temp", false, false, false, "Set<? extends Map<?,java.lang.String>.Entry<?,java.lang.String>>"); }
  @Test
  public void testWithIfBranches() { doTestReplaceAll("temp", true, false, false, "int"); }
  @Test
  public void testInsideTryWithResources() { doTest("temp", true, false, false, "java.io.FileInputStream"); }
  @Test
  public void testInsideForLoop() { doTestReplaceAll("temp", true, false, false, "int"); }
  @Test
  public void testInsideForLoopIndependentFromLoopVariable() { doTestReplaceAll("temp", true, false, false, "int"); }
  @Test
  public void testDistinguishLambdaParams() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testDuplicateGenericExpressions() { doTestReplaceAll("temp", true, false, false, "Foo2<? extends java.lang.Runnable>"); }
  @Test
  public void testStaticImport() { doTest("i", true, true, false, "int"); }
  @Test
  public void testGenericTypeMismatch() { doTest("i", true, true, false, CommonClassNames.JAVA_LANG_OBJECT); }
  @Test
  public void testGenericTypeMismatch1() { doTest("i", true, true, false, "java.util.List<java.lang.String>"); }
  @Test
  public void testThisQualifier() { doTest("count", true, true, false, "int"); }
  @Test
  public void testSubLiteral() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSubLiteral1() { doTest("str", false, false, false, JAVA_LANG_STRING); }

  @Test
  public void testSubLiteralFailure() {
    doTestWithFailure("str", "int");
  }

  @Test
  public void testSubLiteralFromExpression() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSubExpressionFromMagicString() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testSubExpressionFromPrimitiveWithConversion() { doTest("i", false, false, false, "int"); }
  @Test
  public void testSubPrimitiveLiteral() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testArrayFromVarargs() { doTestWithFailure("strings", "java.lang.String[]"); }
  @Test
  public void testArrayFromVarargs1() { doTest("strings", false, false, false, "java.lang.String[]"); }
  @Test
  public void testEnumArrayFromVarargs() { doTest("strings", false, false, false, "E[]"); }
  @Test
  public void testFromFinalFieldOnAssignment() { doTest("strings", false, false, false, JAVA_LANG_STRING); }

  @Test
  public void testNoArrayFromVarargs() {
    doTestWithFailure("strings", "java.lang.String[]");
  }

   @Test
   public void testNoArrayFromVarargs1() {
     doTestWithFailure("strings", "java.lang.String[]");
   }

  @Test
  public void testNoArrayFromVarargsUntilComma() {
    doTestWithFailure("strings", "java.lang.String[]");
  }

  private void doTestWithFailure(String newName, String expectedType) {
    doTestWithFailure(newName, expectedType, "Selected block should represent an expression");
  }

  private void doTestWithFailure(String newName, String expectedType, String errorMessage) {
    try {
      doTest(newName, false, false, false, expectedType);
      fail("Should not be able to perform refactoring");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException  e) {
      assertEquals(RefactoringBundle.getCannotRefactorMessage(errorMessage), e.getMessage());
    }
  }

  @Test
  public void testNonExpression() { doTest("sum", true, true, false, "int"); }
  @Test
  public void testTypeAnnotations() { doTest("y1", true, false, false, "@TA C"); }
  @Test
  public void testReturnStatementWithoutSemicolon() { doTest("b", true, true, false, JAVA_LANG_STRING); }
  @Test
  public void testAndAndSubexpression() { doTest("ab", true, true, false, "boolean"); }
  @Test
  public void testSubexpressionWithSpacesInSelection() { doTest("ab", true, true, false, "boolean"); }
  @Test
  public void testSubexpressionWithSpacesInSelectionAndTailingComment() { doTest("ab", true, true, false, JAVA_LANG_STRING); }
  @Test
  public void testDuplicatesAnonymousClassCreationWithSimilarParameters () {
    doTestReplaceAll("foo1", true, true, false, "Foo");
  }
  @Test
  public void testDifferentForeachParameters() { doTest("toStr", true, true, false, JAVA_LANG_STRING); }
  @Test
  public void testCollapsedToDiamond() { doTest("a", true, true, true, "java.util.ArrayList<java.lang.String>"); }
  @Test
  public void testCantCollapsedToDiamond() { doTest("a", true, true, true, "Foo<java.lang.Number>"); }
  @Test
  public void testFromForInitializer() {
    doTestReplaceAll("list", true, true, true, "java.util.List");
  }
  @Test
  public void testInvalidPostfixExpr() { doTest("a1", true, false, true, "int[]"); }
  @Test
  public void testPolyadic() {
    doTestReplaceAll("b1", true, true, true, "boolean");
  }
  @Test
  public void testAssignmentToUnresolvedReference() { doTest("collection", true, true, true, "java.util.List<? extends java.util.Collection<?>>"); }
  @Test
  public void testSubstringInSwitch() { doTest("ba", false, false, false, JAVA_LANG_STRING);}
  @Test
  public void testEnumValues() { doTest("vs", false, false, false, "E[]"); }

  @Test
  public void testNameSuggestion() {
    String expectedTypeName = "Path";
    doTest(new MockIntroduceVariableHandler("path", true, false, false, expectedTypeName) {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, JavaReplaceChoice replaceChoice) {
        final PsiType type = typeSelectorManager.getDefaultType();
        assertEquals(expectedTypeName, type.getPresentableText(), type.getPresentableText());
        assertEquals("path", CommonJavaRefactoringUtil.getSuggestedName(type, expr).names[0]);
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  @Test
  public void testNameSuggestionConflictingFieldName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    String baseName = "/refactoring/introduceVariable/" + myFixture.getTestName(false);
    myFixture.configureByFile(baseName + ".java");
    myFixture.runInCommand(() -> {
      new MockIntroduceVariableHandler("parentName1", false, false, false, JAVA_LANG_STRING) {
        @Override
        public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                     PsiExpression expr, PsiExpression[] occurrences,
                                                     TypeSelectorManagerImpl typeSelectorManager,
                                                     boolean declareFinalIfAll,
                                                     boolean anyAssignmentLHS,
                                                     InputValidator validator,
                                                     PsiElement anchor, JavaReplaceChoice replaceChoice) {
          final PsiType type = typeSelectorManager.getDefaultType();
          assertEquals("parentName1", CommonJavaRefactoringUtil.getSuggestedName(type, expr).names[0]);
          return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                   validator, anchor, replaceChoice);
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(baseName + ".after.java");
    });
  }

  @Test
  public void testSiblingInnerClassType() {
    doTest(new MockIntroduceVariableHandler("b", true, false, false, "A.B") {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, JavaReplaceChoice replaceChoice) {
        final PsiType type = typeSelectorManager.getDefaultType();
        assertEquals("B", type.getPresentableText(), type.getPresentableText());
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  @Test
  public void testNonExpressionPriorityFailure() {
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int") {
      @Override
      protected void showErrorMessage(@NotNull Project project, Editor editor, @NotNull String message) {
        assertEquals("Cannot perform refactoring.\nExtracting the selected expression changes the semantics of the surrounding expression.", message);
      }
    });
  }

  @Test
  public void testIncorrectExpressionSelected() {
    doTestWithFailure("toString", JAVA_LANG_STRING);
  }

  @Test
  public void testIncompatibleTypesForSelectionSubExpression() {
    doTestWithFailure("s", JAVA_LANG_STRING);
  }

  @Test
  public void testClassSelectionInStaticMethodCall() {
    doTestWithFailure("Foo", "Foo");
  }

  @Test
  public void testMultiCatchSimple() { doTest("e", true, true, false, "java.lang.Exception", true); }
  @Test
  public void testMultiCatchTyped() { doTest("b", true, true, false, "java.lang.Exception", true); }
  @Test
  public void testBeforeVoidStatement() { doTest("c", false, false, false, CommonClassNames.JAVA_LANG_OBJECT); }
  @Test
  public void testWriteUsages() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all occurrences but write"))); doTest("c", true, false, false, JAVA_LANG_STRING); }
  @Test
  public void testLambdaExpr() { doTest("c", false, false, false, "SAM<java.lang.Integer>"); }
  @Test
  public void testMethodRef() { doTest("c", false, false, false, "Test.Bar"); }
  @Test
  public void testLambdaExprNotAccepted() { doTest("c", false, false, false, "SAM<java.lang.Integer>"); }
  @Test
  public void testLambdaNotInContext() { doTest("l", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE); }
  @Test
  public void testMethodRefNotInContext() { doTest("l", false, false, false, "java.util.function.IntConsumer", true); }
  @Test
  public void testMethodRefNotInContextInferred() { doTest("l", false, false, false, "java.util.function.Consumer<java.lang.Integer>", true); }
  @Test
  public void testMethodRefNotInContextInferredNonExact() { doTest("l", false, false, false, "I<java.lang.String>", true); }
  @Test
  public void testIntersectionWildcardExpectedType() { doTest("l", false, false, false, "java.util.List<? extends java.lang.Enum<? extends java.lang.Enum<?>>>", true); }

  @Test
  public void testMethodRefNotInContextInferredFilterWithNonAcceptableSince() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // though test extracts method reference not supposed to appear with language level 7,
    // the "@since 1.8" tag in Consumer prevents it to appear at first position
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, myFixture.getTestRootDisposable());
    doTest("l", false, false, false, "D<java.lang.Integer>", false);
  }

  @Test
  public void testForIterationParameterVar() { doTestWithFailure("input", "Object", "Unknown expression type."); }

  @Test
  public void testPatternUsedInSubsequentConditionCannotExtract() {
    doTestWithFailure("input", "Object", "The selected expression refers to pattern variable 's', which will be out of scope.");
  }

  @Test
  public void testDisallowInInterface() {
    doTestWithFailure("", "", "Cannot extract variable in an interface.");
  }

  @Test
  public void testDisallowInInterfaceAnonymous() {
    doTestWithFailure("", "", "Cannot extract variable in an interface.");
  }

  @Test
  public void testOneLineLambdaVoidCompatible() {UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}"))); doTest("c", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testOneLineLambdaValueCompatible() { doTest("c", false, false, false, "int"); }
  @Test
  public void testStatementsBeforeSuper() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22_PREVIEW, () -> doTest("c", false, false, false, "int"));
  }

  @Test
  public void testPutInLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("J: (Object a) -> {...}")));
    doTest("c", false, false, false, "int");
  }

  @Test
  public void testPutInLambdaBodyMultipleOccurrences() {
    doTestReplaceAll("c", true, false, false, "java.lang.Class<?>");
  }

  @Test
  public void testPutInLambdaBodyVoidValueConflict() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("I<String>: (a) -> {...}")));
    doTest("c", false, false, false, "int");
  }

  @Test
  public void testPutInLambdaBodyVoid() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}")));
    doTest("s", false, false, false, "java.lang.String");
  }

  @Test
  public void testPutInNestedLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}")));
    doTest("s", false, false, false, "java.lang.String");
  }

  @Test
  public void testPutOuterLambda() {
    doTestReplaceAll("s", true, false, false, "java.lang.String");
  }

  @Test
  public void testNormalizeDeclarations() { doTest("i3", false, false, false, "int"); }
  @Test
  public void testNoNameConflict() { doTest("cTest", false, false, false, "cTest"); }
  @Test
  public void testMethodReferenceExpr() { doTest("m", false, false, false, "Foo.I"); }
  @Test
  public void testDenotableType1() { doTest("m", false, false, false, "A<? extends A<? extends java.lang.Object>>"); }
  @Test
  public void testKeepComments() { doTest("m", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testDenotableType2() { doTest("m", false, false, false, "I<? extends I<?>>"); }
  @Test
  public void testExpectedTypeInsteadOfNullForVarargs() { doTest("s", false, false, false, JAVA_LANG_STRING); }
  @Test
  public void testDenotableType3() { doTest("m", false, false, false, "java.util.function.IntFunction<java.lang.Class<?>[]>"); }
  @Test
  public void testCapturedWildcardUpperBoundSuggestedAsType() { doTest("m", false, false, false, "I"); }
  @Test
  public void testArrayOfCapturedWildcardUpperBoundSuggestedAsType() { doTest("m", false, false, false, "I[]"); }
  @Test
  public void testFieldFromLambda() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("IntSupplier: () -> {...}")));
    doTest("foo", false, false, true, "int"); }
  @Test
  public void testNestedAndOrParentheses() { doTest("foo", false, false, false, "boolean"); }

  @Test
  public void testReturnNonExportedArray() {
    doTest(new MockIntroduceVariableHandler("i", false, false, false, "java.io.File[]"));
  }

  @Test
  public void testTypesHierarchyBasedOnCalledMethod() {
    doTest(new MockIntroduceVariableHandler("v", true, false, false, "B") {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, JavaReplaceChoice replaceChoice) {
        final PsiType[] types = typeSelectorManager.getTypesForAll();
        assertEquals("B", types[0].getPresentableText(), types[0].getPresentableText());
        assertEquals("A", types[1].getPresentableText(), types[1].getPresentableText());
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  @Test
  public void testChooseIntersectionConjunctBasedOnFollowingCalls() { doTest("m", false, false, false, "IA"); }
  @Test
  public void testTooPopularNameOfTheFollowingCall() { doTest("l", false, false, false, "java.util.List<java.lang.String>"); }
  @Test
  public void testChooseTypeExpressionWhenNotDenotable() { doTest("m", false, false, false, "Foo"); }
  @Test
  public void testChooseTypeExpressionWhenNotDenotable1() { doTest("m", false, false, false, "Foo<?>"); }

  @Test
  public void testNullabilityAnnotationConflict() {
    doTest("x", true, false, false, "java.lang.@org.eclipse.jdt.annotation.Nullable String");
  }

  @Test
  public void testNullabilityAnnotationNoConflict() {
    doTest("x", true, false, false, "java.lang.@org.eclipse.jdt.annotation.NonNull String");
  }

  @Test
  public void testAllButWriteNoRead() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 2 occurrences (will change semantics!)")));
    doTest("x", true, false, true, "int");
  }

  @Test
  public void testIntroduceVariableInsideImplicitClass() {
    doTest("x", true, false, false, "java.lang.Class<Nested>");
  }

  private void doTestWithVarType(IntroduceVariableBase testMe) {
    Boolean asVarType = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE;
    try {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
      doTest(testMe);
    }
    finally {
      JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = asVarType;
    }
  }

  private void doTestReplaceAll(String newName, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String typeText) {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest(newName, replaceAll, declareFinal, replaceLValues, typeText);
  }

  private void doTest(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText) {
    doTest(name, replaceAll, declareFinal, replaceLValues, expectedTypeText, false);
  }

  private void doTest(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText, boolean lookForType) {
    doTest(new MockIntroduceVariableHandler(name, replaceAll, declareFinal, replaceLValues, expectedTypeText, lookForType));
  }

  private void doTest(IntroduceVariableBase testMe) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String baseName = "/refactoring/introduceVariable/" + myFixture.getTestName(false);
    myFixture.configureByFile(baseName + ".java");
    myFixture.runInCommand(() -> {
      testMe.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      if (state != null) {
        state.gotoEnd(false);
      }
      myFixture.checkResultByFile(baseName + ".after.java");
    });
  }
}