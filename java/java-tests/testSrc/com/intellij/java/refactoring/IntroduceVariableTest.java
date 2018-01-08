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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author dsl
 */
public class IntroduceVariableTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimpleExpression() {
    doTest(new MockIntroduceVariableHandler("i", false, false, true, "int"));
  }

  public void testInsideFor() {
    doTest(new MockIntroduceVariableHandler("temp", false, false, true, "int"));
  }

  public void testReplaceAll() {
    doTest(new MockIntroduceVariableHandler("s", true, true, true, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testIDEADEV3678() {
    doTest(new MockIntroduceVariableHandler("component", true, true, true, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testIDEADEV13369() {
    doTest(new MockIntroduceVariableHandler("ints", true, true, true, "int[]"));
  }

  public void testAnonymousClass() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, true, "int"));
  }

  public void testAnonymousClass1() {
    doTest(new MockIntroduceVariableHandler("runnable", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE));
  }

  public void testAnonymousClass2() {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "int"));
  }

  public void testAnonymousClass3() {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "Foo"));
  }

  public void testAnonymousClass4() {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "int"));
  }

  public void testAnonymousClass5() {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "int"));
  }

  public void testAnonymousClass6() {
    doTest(new MockIntroduceVariableHandler("str", true, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testAnonymousClass7() {
    doTest(new MockIntroduceVariableHandler("str", true, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testLambda() {
    doTest(new MockIntroduceVariableHandler("j", true, false, false, "int"));
  }

  public void testParenthized() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testExpectedType8Inference() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false,
                                            "java.util.Map<java.lang.String,java.util.List<java.lang.String>>"));
  }

  public void testMethodCall() {
    doTest(new MockIntroduceVariableHandler("temp", true, true, true, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testMethodCallInSwitch() {
    doTest(new MockIntroduceVariableHandler("i", true, true, true, "int"));
  }

  public void testParenthizedOccurence() {
    doTest(new MockIntroduceVariableHandler("empty", true, true, true, "boolean"));
  }

  public void testParenthizedOccurence1() {
    doTest(new MockIntroduceVariableHandler("s", true, true, true, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testAfterSemicolon() {
    doTest(new MockIntroduceVariableHandler("s", true, true, true, CommonClassNames.JAVA_LANG_RUNNABLE));
  }

  public void testConflictingField() {
    doTest(new MockIntroduceVariableHandler("name", true, false, true, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testConflictingFieldInExpression() {
    doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testStaticConflictingField() {
    doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testNonConflictingField() {
     doTest(new MockIntroduceVariableHandler("name", false, false, true, "int"));
  }

  public void testScr16910() {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "int"));
  }

  public void testSCR18295() {
    doTest(new MockIntroduceVariableHandler("it", true, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSCR18295a() {
    doTest(new MockIntroduceVariableHandler("it", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testFromInjected() {
    doTest(new MockIntroduceVariableHandler("regexp", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSCR10412() {
    doTest(new MockIntroduceVariableHandler("newVar", false, false, false, "java.lang.String[]"));
  }

  public void testSCR22718() {
    doTest(new MockIntroduceVariableHandler("object", true, true, false, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testSCR26075() {
    doTest(new MockIntroduceVariableHandler("wrong", false, false, false, CommonClassNames.JAVA_LANG_STRING) {
      @Override
      protected void assertValidationResult(boolean validationResult) {
        assertFalse(validationResult);
      }

      @Override
      protected boolean reportConflicts(MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
        assertEquals(2, conflicts.size());
        Collection<? extends String> conflictsMessages = conflicts.values();
        assertTrue(conflictsMessages.contains("Introducing variable may break code logic"));
        assertTrue(conflictsMessages.contains("Local variable <b><code>c</code></b> is modified in loop body"));
        return false;
      }
    });
  }

  public void testConflictingFieldInOuterClass() {
    doTest(new MockIntroduceVariableHandler("text", true, true, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSkipSemicolon() {
    doTest(new MockIntroduceVariableHandler("mi5", false, false, false, "int"));
  }

  public void testInsideIf() {
    doTest(new MockIntroduceVariableHandler("s1", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testInsideElse() {
    doTest(new MockIntroduceVariableHandler("s1", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testInsideWhile() {
    doTest(new MockIntroduceVariableHandler("temp", false, false, false, "int"));
  }

  public void testSCR40281() {
    doTest(new MockIntroduceVariableHandler("temp", false, false, false, "Set<? extends Map<?,java.lang.String>.Entry<?,java.lang.String>>"));
  }

  public void testWithIfBranches() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testInsideForLoop() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testInsideForLoopIndependantFromLoopVariable() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "int"));
  }

  public void testDistinguishLambdaParams() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testDuplicateGenericExpressions() {
    doTest(new MockIntroduceVariableHandler("temp", true, false, false, "Foo2<? extends java.lang.Runnable>"));
  }

  public void testStaticImport() {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "int"));
  }

  public void testGenericTypeMismatch() {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "java.lang.String"));
  }

  public void testGenericTypeMismatch1() {
    doTest(new MockIntroduceVariableHandler("i", true, true, false, "java.util.List<java.lang.String>"));
  }

  public void testThisQualifier() {
    doTest(new MockIntroduceVariableHandler("count", true, true, false, "int"));
  }

  public void testSubLiteral() {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSubLiteral1() {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSubLiteralFailure() {
    try {
      doTest(new MockIntroduceVariableHandler("str", false, false, false, "int"));
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testSubLiteralFromExpression() {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSubExpressionFromIntellijidearulezzz() {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testSubPrimitiveLiteral() {
    doTest(new MockIntroduceVariableHandler("str", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testArrayFromVarargs() {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
  }

  public void testArrayFromVarargs1() {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
  }

  public void testEnumArrayFromVarargs() {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, "E[]"));
  }

  public void testFromFinalFieldOnAssignment() {
    doTest(new MockIntroduceVariableHandler("strs", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testNoArrayFromVarargs() {
    try {
      doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

   public void testNoArrayFromVarargs1() {
    try {
      doTest(new MockIntroduceVariableHandler("strs", false, false, false, "java.lang.String[]"));
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testNonExpression() {
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int"));
  }

  public void testTypeAnnotations() {
    doTest(new MockIntroduceVariableHandler("y1", true, false, false, "@TA C"));
  }

  public void testReturnStatementWithoutSemicolon() {
    doTest(new MockIntroduceVariableHandler("b", true, true, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testAndAndSubexpression() {
    doTest(new MockIntroduceVariableHandler("ab", true, true, false, "boolean"));
  }

  public void testSubexpressionWithSpacesInSelection() {
    doTest(new MockIntroduceVariableHandler("ab", true, true, false, "boolean"));
  }

  public void testSubexpressionWithSpacesInSelectionAndTailingComment() {
    doTest(new MockIntroduceVariableHandler("ab", true, true, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testDuplicatesAnonymousClassCreationWithSimilarParameters () {
    doTest(new MockIntroduceVariableHandler("foo1", true, true, false, "Foo"));
  }

  public void testDifferentForeachParameters () {
    doTest(new MockIntroduceVariableHandler("tostr", true, true, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testCollapsedToDiamond() {
    doTest(new MockIntroduceVariableHandler("a", true, true, true, "java.util.ArrayList<java.lang.String>"));
  }

  public void testCantCollapsedToDiamond() {
    doTest(new MockIntroduceVariableHandler("a", true, true, true, "Foo<java.lang.Number>"));
  }

  public void testFromForInitializer() {
    doTest(new MockIntroduceVariableHandler("list", true, true, true, "java.util.List"));
  }

  public void testInvalidPostfixExpr() {
    doTest(new MockIntroduceVariableHandler("a1", true, false, true, "int[]"));
  }

  public void testPolyadic() {
    doTest(new MockIntroduceVariableHandler("b1", true, true, true, "boolean"));
  }

  public void testAssignmentToUnresolvedReference() {
    doTest(new MockIntroduceVariableHandler("collection", true, true, true, "java.util.List<? extends java.util.Collection<?>>"));
  }

  public void testNameSuggestion() {
    final String expectedTypeName = "Path";
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
        assertEquals("path", getSuggestedName(type, expr).names[0]);
        return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS,
                                 validator, anchor, replaceChoice);
      }
    });
  }

  public void testSiblingInnerClassType() {
    doTest(new MockIntroduceVariableHandler("vari", true, false, false, "A.B") {
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
    doTest(new MockIntroduceVariableHandler("sum", true, true, false, "int"){
      @Override
      protected void showErrorMessage(Project project, Editor editor, String message) {
        assertEquals("Cannot perform refactoring.\nExtracting selected expression would change the semantic of the whole expression.", message);
      }
    });
  }

  public void testIncorrectExpressionSelected() {
    try {
      doTest(new MockIntroduceVariableHandler("toString", false, false, false, CommonClassNames.JAVA_LANG_STRING));
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testIncompatibleTypesForSelectionSubExpression() {
    try {
      doTest(new MockIntroduceVariableHandler("s", false, false, false, CommonClassNames.JAVA_LANG_STRING));
    }
    catch (Exception e) {
      assertEquals("Error message:Cannot perform refactoring.\nSelected block should represent an expression", e.getMessage());
      return;
    }
    fail("Should not be able to perform refactoring");
  }

  public void testMultiCatchSimple() {
    doTest(new MockIntroduceVariableHandler("e", true, true, false, "java.lang.Exception", true));
  }

  public void testMultiCatchTyped() {
    doTest(new MockIntroduceVariableHandler("b", true, true, false, "java.lang.Exception", true));
  }

  public void testBeforeVoidStatement() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testWriteUsages() {
    doTest(new MockIntroduceVariableHandler("c", true, false, false, CommonClassNames.JAVA_LANG_STRING));
  }

  public void testLambdaExpr() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "SAM<java.lang.Integer>"));
  }

  public void testMethodRef() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "Test.Bar"));
  }

  public void testLambdaExprNotAccepted() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "SAM<java.lang.Integer>"));
  }

  public void testLambdaNotInContext() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, CommonClassNames.JAVA_LANG_RUNNABLE));
  }

  public void testMethodRefNotInContext() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "java.util.function.IntConsumer", true));
  }

  public void testMethodRefNotInContextInferred() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "java.util.function.Consumer<java.lang.Integer>", true));
  }

  public void testMethodRefNotInContextInferredNonExact() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "I<java.lang.String>", true));
  }

  public void testIntersectionWildcardExpectedType() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "java.util.List<? extends java.lang.Enum<? extends java.lang.Enum<?>>>", true));
  }

  public void testMethodRefNotInContextInferredFilterWithNonAcceptableSince() {
    //though test extracts method reference which is not suppose to appear with language level 1.7
    //@since 1.8 in Consumer prevent it to appear at first position
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "D<java.lang.Integer>", false));
  }

  public void testOneLineLambdaVoidCompatible() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, CommonClassNames.JAVA_LANG_STRING));
  }
  public void testOneLineLambdaValueCompatible() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "int"));
  }

  public void testPutInLambdaBody() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "int"));
  }

  public void testPutInLambdaBodyMultipleOccurrences() {
    doTest(new MockIntroduceVariableHandler("c", true, false, false, "java.lang.Class<?>"));
  }

  public void testPutInLambdaBodyVoidValueConflict() {
    doTest(new MockIntroduceVariableHandler("c", false, false, false, "int"));
  }

  public void testPutInLambdaBodyVoid() {
    doTest(new MockIntroduceVariableHandler("s", false, false, false, "java.lang.String"));
  }

  public void testPutInNestedLambdaBody() {
    doTest(new MockIntroduceVariableHandler("s", false, false, false, "java.lang.String"));
  }

  public void testPutOuterLambda() {
    doTest(new MockIntroduceVariableHandler("s", true, false, false, "java.lang.String"));
  }

  public void testNormalizeDeclarations() {
    doTest(new MockIntroduceVariableHandler("i3", false, false, false, "int"));
  }

  public void testNoNameConflict() {
    doTest(new MockIntroduceVariableHandler("cTest", false, false, false, "cTest"));
  }

  public void testMethodReferenceExpr() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "Foo.I"));
  }

  public void testDenotableType1() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "A<? extends A<? extends java.lang.Object>>"));
  }

  public void testDenotableType2() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "I<? extends I<?>>"));
  }

  public void testDenotableType3() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "java.util.function.IntFunction<java.lang.Class<?>[]>"));
  }

  public void testCapturedWildcardUpperBoundSuggestedAsType() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "I"));
  }

  public void testReturnNonExportedArray() {
    doTest(new MockIntroduceVariableHandler("i", false, false, false, "java.io.File[]") {
      @Override
      protected boolean isInplaceAvailableInTestMode() {
        return true;
      }
    });
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

  public void testChooseIntersectionConjunctBasedOnFollowingCalls() {
    doTest(new MockIntroduceVariableHandler("m", false, false, false, "IA"));
  }

  public void testTooPopularNameOfTheFollowingCall() {
    doTest(new MockIntroduceVariableHandler("l", false, false, false, "java.util.List<java.lang.String>"));
  }

  public void testChooseTypeExpressionWhenNotDenotable() { doTest(new MockIntroduceVariableHandler("m", false, false, false, "Foo")); }
  public void testChooseTypeExpressionWhenNotDenotable1() { doTest(new MockIntroduceVariableHandler("m", false, false, false, "Foo<?>")); }

  private void doTest(IntroduceVariableBase testMe) {
    String baseName = "/refactoring/introduceVariable/" + getTestName(false);
    configureByFile(baseName + ".java");
    testMe.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(baseName + ".after.java");
  }
}