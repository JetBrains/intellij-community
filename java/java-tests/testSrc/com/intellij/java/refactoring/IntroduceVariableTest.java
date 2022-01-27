// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
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
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * @author dsl
 */
public class IntroduceVariableTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimpleExpression() { doTest("i", false, false, true, "int"); }
  public void testInsideFor() { doTest("temp", false, false, true, "int"); }
  public void testReplaceAll() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("s", true, true, true, JAVA_LANG_STRING); 
  }
  public void testIDEADEV3678() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("component", true, true, true, CommonClassNames.JAVA_LANG_OBJECT); 
  }
  public void testIDEADEV13369() { doTest("ints", true, true, true, "int[]"); }
  public void testAnonymousClass() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, true, "int"); 
  }
  public void testAnonymousClass1() { doTest("runnable", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE); }
  public void testAnonymousClass2() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("j", true, false, false, "int"); 
  }
  public void testAnonymousClass3() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("j", true, false, false, "Foo"); 
  }
  public void testAnonymousClass4() { doTest("j", true, false, false, "int"); }
  public void testAnonymousClass5() { doTest("j", true, false, false, "int"); }
  public void testAnonymousClass6() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("str", true, false, false, JAVA_LANG_STRING); 
  }
  public void testAnonymousClass7() { doTest("str", true, false, false, JAVA_LANG_STRING); }
  public void testLambda() { doTest("j", true, false, false, "int"); }
  public void testParenthesized() { doTest("temp", true, false, false, "int"); }
  public void testExpectedType8Inference() { doTest("temp", true, false, false, "java.util.Map<java.lang.String,java.util.List<java.lang.String>>"); }
  public void testMethodCall() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, true, true, CommonClassNames.JAVA_LANG_OBJECT); 
  }
  public void testMethodCallInSwitch() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("i", true, true, true, "int"); 
  }
  public void testFunctionalExpressionInSwitch() { doTest("p", true, true, true, "java.util.function.Predicate<java.lang.String>"); }
  public void testParenthesizedOccurrence1() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("empty", true, true, true, "boolean"); 
  }
  public void testParenthesizedOccurrence2() { doTest("s", true, true, true, JAVA_LANG_STRING); }
  public void testAfterSemicolon() {
    UiInterceptors.register(new ChooserInterceptor(null, StringUtil.escapeToRegexp("new Runnable() {...}")));
    doTest("s", true, true, true, CommonClassNames.JAVA_LANG_RUNNABLE); 
  }
  public void testConflictingField() { doTest("name", true, false, true, JAVA_LANG_STRING); }
  public void testConflictingFieldInExpression() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest("name", false, false, true, "int"); 
  }
  public void testStaticConflictingField() { doTest("name", false, false, true, "int"); }
  public void testNonConflictingField() { doTest("name", false, false, true, "int"); }
  public void testScr16910() { doTest("i", true, true, false, "int"); }
  public void testSCR18295() { doTest("it", true, false, false, JAVA_LANG_STRING); }
  public void testSCR18295a() { doTest("it", false, false, false, JAVA_LANG_STRING); }
  public void testFromInjected() { doTest("regexp", false, false, false, JAVA_LANG_STRING); }
  public void testSCR10412() { doTest("newVar", false, false, false, "java.lang.String[]"); }
  public void testSCR22718() { 
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("object", true, true, false, CommonClassNames.JAVA_LANG_OBJECT); 
  }
  public void testSCR26075() { doTest("ok", false, false, false, JAVA_LANG_STRING); }

  public void testSCR26075For() {
    doTest(new MockIntroduceVariableHandler("wrong", false, false, false, JAVA_LANG_STRING) {
      @Override
      protected void assertValidationResult(boolean validationResult) {
        assertFalse(validationResult);
      }

      @Override
      protected void showErrorMessage(Project project, Editor editor, String message) {
        assertEquals("Introducing variable may break code logic<br>Local variable <b><code>c</code></b> is modified in loop body", message);
      }
    });
  }

  public void testConflictingFieldInOuterClass() { doTest("text", true, true, false, JAVA_LANG_STRING); }
  public void testSkipSemicolon() { doTest("mi5", false, false, false, "int"); }
  public void testSkipErroneousParen() { doTest("x", false, false, false, JAVA_LANG_STRING); }
  public void testInsideIf() { doTest("s1", false, false, false, JAVA_LANG_STRING); }
  public void testInsideElse() { doTest("s1", false, false, false, JAVA_LANG_STRING); }
  public void testInsideWhile() { doTest("temp", false, false, false, "int"); }

  public void testWhileCondition() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, "Node");
  }

  public void testWhileCondition2() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, "Node");
  }

  public void testWhileConditionIncomplete() { doTest("temp", true, false, false, "boolean"); }

  public void testWhileConditionNoBrace() { doTest("temp", true, false, false, "int"); }

  public void testWhileConditionPlusNormal() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, "int");
  }

  public void testWhileConditionPlusNormal2() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, "int");
  }

  public void testWhileConditionAndOr() { doTest("temp", true, false, false, "boolean"); }

  public void testField() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", false, false, false, JAVA_LANG_STRING);
  }

  public void testFieldAll() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, JAVA_LANG_STRING);
  }

  public void testCaseLabel() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("temp", true, false, false, "int");
  }
  public void testCaseLabelSingle() { doTest(new IntroduceVariableHandler()); }
  public void testCaseLabelRuleSingle() { doTest(new IntroduceVariableHandler()); }
  public void testCaseLabelRuleExpression() { doTest(new IntroduceVariableHandler()); }

  public void testCaseLabelEnum() {
    try {
      doTest("temp", true, false, false, "");
    }
    catch (RuntimeException e) {
      assertEquals("Error message:" +
                   RefactoringBundle.message("cannot.perform.refactoring") + "\n" +
                   JavaRefactoringBundle.message("refactoring.introduce.variable.enum.in.label.message"), e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testPatternVariableUsedAfterwards() {
    try {
      doTest("temp", true, false, false, "");
    }
    catch (RuntimeException e) {
      assertEquals("Error message:" +
                   RefactoringBundle.message("cannot.perform.refactoring") + "\n" +
                   JavaRefactoringBundle.message("selected.expression.introduces.pattern.variable", "s"), e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }
  public void testPatternVariableNotUsedAfterwards() {
    doTest("temp", true, false, false, "boolean");
  }
  public void testPatternVariableDeclarationJava16() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, JAVA_LANG_STRING));
  }
  public void testPatternVariableDeclarationUpcastJava16() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, JAVA_LANG_STRING));
  }
  public void testPatternVariableDeclarationUsedInLocalJava16() { doTest("temp", true, false, false, JAVA_LANG_STRING);}
  public void testPatternVariableDeclarationAfterIfJava16() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, JAVA_LANG_STRING);}
  public void testNonPatternVariableDeclarationTwoBlocksJava16() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, JAVA_LANG_STRING);}
  public void testNonPatternDeclarationJava16() { doTest("temp", true, false, false, JAVA_LANG_STRING);}

  public void testTernaryBothBranches() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, "int"); }
  public void testIfConditionAndChain() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> doTest("temp", true, false, false, JAVA_LANG_STRING)); }
  public void testReturnAndChain() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> doTest("temp", true, false, false, JAVA_LANG_STRING)); }
  public void testReturnOrChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testReturnOrAndChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testReturnTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testFieldInitializer() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testFieldInitializerDenormalized() { doTest("temp", true, false, false, "int"); }
  public void testAssignTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testEnsureCodeBlockAroundBreakStatement() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testEnsureCodeBlockForThrows() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testFromSwitchStatement() { doTest("temp", true, false, false, JAVA_LANG_STRING); }

  public void testVarTypeExtractedJava10() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "java.util.ArrayList<java.lang.String>"));
  }
  
  public void testVarTypeArrayExtractedJava10() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "int[]"));
  }

  public void testVarTypeLambdaTypeCast() {
    doTestWithVarType(new MockIntroduceVariableHandler("temp", true, false, false, "I"));
  }

  public void testTypeContainingVarJava11() {
    doTest("temp", true, false, false, "var.X");
  }

  public void testDeclareTernary() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testLambdaAndChain() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testSCR40281() { doTest("temp", false, false, false, "Set<? extends Map<?,java.lang.String>.Entry<?,java.lang.String>>"); }
  public void testWithIfBranches() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, "int"); }
  public void testInsideTryWithResources() { doTest("temp", true, false, false, "java.io.FileInputStream"); }
  public void testInsideForLoop() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, "int"); }
  public void testInsideForLoopIndependentFromLoopVariable() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, "int"); }
  public void testDistinguishLambdaParams() { doTest("temp", true, false, false, JAVA_LANG_STRING); }
  public void testDuplicateGenericExpressions() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));doTest("temp", true, false, false, "Foo2<? extends java.lang.Runnable>"); }
  public void testStaticImport() { doTest("i", true, true, false, "int"); }
  public void testGenericTypeMismatch() { doTest("i", true, true, false, CommonClassNames.JAVA_LANG_OBJECT); }
  public void testGenericTypeMismatch1() { doTest("i", true, true, false, "java.util.List<java.lang.String>"); }
  public void testThisQualifier() { doTest("count", true, true, false, "int"); }
  public void testSubLiteral() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  public void testSubLiteral1() { doTest("str", false, false, false, JAVA_LANG_STRING); }

  public void testSubLiteralFailure() {
    doTestWithFailure("str", "int");
  }

  public void testSubLiteralFromExpression() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  public void testSubExpressionFromMagicString() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  public void testSubExpressionFromPrimitiveWithConversion() { doTest("i", false, false, false, "int"); }
  public void testSubPrimitiveLiteral() { doTest("str", false, false, false, JAVA_LANG_STRING); }
  public void testArrayFromVarargs() { doTest("strings", false, false, false, "java.lang.String[]"); }
  public void testArrayFromVarargs1() { doTest("strings", false, false, false, "java.lang.String[]"); }
  public void testEnumArrayFromVarargs() { doTest("strings", false, false, false, "E[]"); }
  public void testFromFinalFieldOnAssignment() { doTest("strings", false, false, false, JAVA_LANG_STRING); }

  public void testNoArrayFromVarargs() {
    doTestWithFailure("strings", "java.lang.String[]");
  }

   public void testNoArrayFromVarargs1() {
     doTestWithFailure("strings", "java.lang.String[]");
   }

  public void testNoArrayFromVarargsUntilComma() {
    doTestWithFailure("strings", "java.lang.String[]");
  }

  public void doTestWithFailure(String newName, String expectedType) {
    try {
      doTest(newName, false, false, false, expectedType);
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testNonExpression() { doTest("sum", true, true, false, "int"); }
  public void testTypeAnnotations() { doTest("y1", true, false, false, "@TA C"); }
  public void testReturnStatementWithoutSemicolon() { doTest("b", true, true, false, JAVA_LANG_STRING); }
  public void testAndAndSubexpression() { doTest("ab", true, true, false, "boolean"); }
  public void testSubexpressionWithSpacesInSelection() { doTest("ab", true, true, false, "boolean"); }
  public void testSubexpressionWithSpacesInSelectionAndTailingComment() { doTest("ab", true, true, false, JAVA_LANG_STRING); }
  public void testDuplicatesAnonymousClassCreationWithSimilarParameters () {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("foo1", true, true, false, "Foo"); 
  }
  public void testDifferentForeachParameters() { doTest("toStr", true, true, false, JAVA_LANG_STRING); }
  public void testCollapsedToDiamond() { doTest("a", true, true, true, "java.util.ArrayList<java.lang.String>"); }
  public void testCantCollapsedToDiamond() { doTest("a", true, true, true, "Foo<java.lang.Number>"); }
  public void testFromForInitializer() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("list", true, true, true, "java.util.List"); 
  }
  public void testInvalidPostfixExpr() { doTest("a1", true, false, true, "int[]"); }
  public void testPolyadic() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("b1", true, true, true, "boolean"); 
  }
  public void testAssignmentToUnresolvedReference() { doTest("collection", true, true, true, "java.util.List<? extends java.util.Collection<?>>"); }
  public void testSubstringInSwitch() { doTest("ba", false, false, false, JAVA_LANG_STRING);}
  public void testEnumValues() { doTest("vs", false, false, false, "E[]"); }
  
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
                                                   PsiElement anchor, final JavaReplaceChoice replaceChoice) {
        final PsiType type = typeSelectorManager.getDefaultType();
        assertEquals(type.getPresentableText(), expectedTypeName, type.getPresentableText());
        assertEquals("path", CommonJavaRefactoringUtil.getSuggestedName(type, expr).names[0]);
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  public void testSiblingInnerClassType() {
    doTest(new MockIntroduceVariableHandler("b", true, false, false, "A.B") {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, final JavaReplaceChoice replaceChoice) {
        final PsiType type = typeSelectorManager.getDefaultType();
        assertEquals(type.getPresentableText(), "B", type.getPresentableText());
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  public void testNonExpressionPriorityFailure() {
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int") {
      @Override
      protected void showErrorMessage(Project project, Editor editor, String message) {
        assertEquals("Cannot perform refactoring.\nExtracting selected expression would change the semantic of the whole expression.", message);
      }
    });
  }

  public void testIncorrectExpressionSelected() {
    doTestWithFailure("toString", JAVA_LANG_STRING);
  }

  public void testIncompatibleTypesForSelectionSubExpression() {
    doTestWithFailure("s", JAVA_LANG_STRING);
  }

  public void testClassSelectionInStaticMethodCall() {
    doTestWithFailure("Foo", "Foo");
  }

  public void testMultiCatchSimple() { doTest("e", true, true, false, "java.lang.Exception", true); }
  public void testMultiCatchTyped() { doTest("b", true, true, false, "java.lang.Exception", true); }
  public void testBeforeVoidStatement() { doTest("c", false, false, false, CommonClassNames.JAVA_LANG_OBJECT); }
  public void testWriteUsages() { UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all occurrences but write"))); doTest("c", true, false, false, JAVA_LANG_STRING); }
  public void testLambdaExpr() { doTest("c", false, false, false, "SAM<java.lang.Integer>"); }
  public void testMethodRef() { doTest("c", false, false, false, "Test.Bar"); }
  public void testLambdaExprNotAccepted() { doTest("c", false, false, false, "SAM<java.lang.Integer>"); }
  public void testLambdaNotInContext() { doTest("l", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE); }
  public void testMethodRefNotInContext() { doTest("l", false, false, false, "java.util.function.IntConsumer", true); }
  public void testMethodRefNotInContextInferred() { doTest("l", false, false, false, "java.util.function.Consumer<java.lang.Integer>", true); }
  public void testMethodRefNotInContextInferredNonExact() { doTest("l", false, false, false, "I<java.lang.String>", true); }
  public void testIntersectionWildcardExpectedType() { doTest("l", false, false, false, "java.util.List<? extends java.lang.Enum<? extends java.lang.Enum<?>>>", true); }

  public void testMethodRefNotInContextInferredFilterWithNonAcceptableSince() {
    // though test extracts method reference not supposed to appear with language level 7,
    // the "@since 1.8" tag in Consumer prevents it to appear at first position
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest("l", false, false, false, "D<java.lang.Integer>", false);
  }

  public void testForIterationParameterVar() {
    try {
      doTest("input", false, false, false, "Object", false);
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nUnknown expression type.", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testOneLineLambdaVoidCompatible() {UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}"))); doTest("c", false, false, false, JAVA_LANG_STRING); }
  public void testOneLineLambdaValueCompatible() { doTest("c", false, false, false, "int"); }

  public void testPutInLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("J: (Object a) -> {...}")));
    doTest("c", false, false, false, "int");
  }

  public void testPutInLambdaBodyMultipleOccurrences() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("c", true, false, false, "java.lang.Class<?>");
  }

  public void testPutInLambdaBodyVoidValueConflict() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("I<String>: (a) -> {...}")));
    doTest("c", false, false, false, "int");
  }

  public void testPutInLambdaBodyVoid() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}")));
    doTest("s", false, false, false, "java.lang.String");
  }

  public void testPutInNestedLambdaBody() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Runnable: () -> {...}")));
    doTest("s", false, false, false, "java.lang.String");
  }

  public void testPutOuterLambda() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest("s", true, false, false, "java.lang.String");
  }

  public void testNormalizeDeclarations() { doTest("i3", false, false, false, "int"); }
  public void testNoNameConflict() { doTest("cTest", false, false, false, "cTest"); }
  public void testMethodReferenceExpr() { doTest("m", false, false, false, "Foo.I"); }
  public void testDenotableType1() { doTest("m", false, false, false, "A<? extends A<? extends java.lang.Object>>"); }
  public void testKeepComments() { doTest("m", false, false, false, JAVA_LANG_STRING); }
  public void testDenotableType2() { doTest("m", false, false, false, "I<? extends I<?>>"); }
  public void testExpectedTypeInsteadOfNullForVarargs() { doTest("s", false, false, false, JAVA_LANG_STRING); }
  public void testDenotableType3() { doTest("m", false, false, false, "java.util.function.IntFunction<java.lang.Class<?>[]>"); }
  public void testCapturedWildcardUpperBoundSuggestedAsType() { doTest("m", false, false, false, "I"); }
  public void testArrayOfCapturedWildcardUpperBoundSuggestedAsType() { doTest("m", false, false, false, "I[]"); }
  public void testFieldFromLambda() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("IntSupplier: () -> {...}")));
    doTest("foo", false, false, true, "int"); }
  public void testNestedAndOrParentheses() { doTest("foo", false, false, false, "boolean"); }

  public void testReturnNonExportedArray() {
    doTest(new MockIntroduceVariableHandler("i", false, false, false, "java.io.File[]"));
  }

  public void testTypesHierarchyBasedOnCalledMethod() {
    doTest(new MockIntroduceVariableHandler("v", true, false, false, "B") {
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                                   PsiExpression expr, PsiExpression[] occurrences,
                                                   TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll,
                                                   boolean anyAssignmentLHS,
                                                   InputValidator validator,
                                                   PsiElement anchor, final JavaReplaceChoice replaceChoice) {
        final PsiType[] types = typeSelectorManager.getTypesForAll();
        assertEquals(types[0].getPresentableText(), "B", types[0].getPresentableText());
        assertEquals(types[1].getPresentableText(), "A", types[1].getPresentableText());
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  public void testChooseIntersectionConjunctBasedOnFollowingCalls() { doTest("m", false, false, false, "IA"); }
  public void testTooPopularNameOfTheFollowingCall() { doTest("l", false, false, false, "java.util.List<java.lang.String>"); }
  public void testChooseTypeExpressionWhenNotDenotable() { doTest("m", false, false, false, "Foo"); }
  public void testChooseTypeExpressionWhenNotDenotable1() { doTest("m", false, false, false, "Foo<?>"); }

  public void testNullabilityAnnotationConflict() {
    doTest("x", true, false, false, "java.lang.@org.eclipse.jdt.annotation.Nullable String"); 
  }

  public void testNullabilityAnnotationNoConflict() {
    doTest("x", true, false, false, "java.lang.@org.eclipse.jdt.annotation.NonNull String"); 
  }

  public void testAllButWriteNoRead() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 2 occurrences (will change semantics!)")));
    doTest("x", true, false, true, "int");
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

  private void doTest(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText) {
    doTest(name, replaceAll, declareFinal, replaceLValues, expectedTypeText, false);
  }

  private void doTest(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText, boolean lookForType) {
    doTest(new MockIntroduceVariableHandler(name, replaceAll, declareFinal, replaceLValues, expectedTypeText, lookForType));
  }

  private void doTest(IntroduceVariableBase testMe) {
    String baseName = "/refactoring/introduceVariable/" + getTestName(false);
    configureByFile(baseName + ".java");
    testMe.invoke(getProject(), getEditor(), getFile(), null);
    TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    if (state == null) return;
    state.gotoEnd(false);
    checkResultByFile(baseName + ".after.java");
  }
}