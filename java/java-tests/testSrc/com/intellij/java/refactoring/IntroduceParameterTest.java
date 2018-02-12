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
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author dsl
 * @since 07.05.2002
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceParameterTest extends LightRefactoringTestCase  {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  public void testNoUsages() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false,
           "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. " +
           "Value for introduced parameter in that method call will be incorrect.");
  }

  public void testRemoveOverrideFromDelegated() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  public void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testNull() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testLocalVarDeclaration() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testQualifiedNew() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testCollapseToLambda() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testExpandMethodReference() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testBareRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  public void testNewWithRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  public void testMethodCallRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  public void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false, false);
  }

  public void testParameterInFor() {
    configureByFile("/refactoring/introduceParameter/beforeParameterInFor.java");
    performForLocal(true, true, true, false, false);
    checkResultByFile("/refactoring/introduceParameter/afterParameterInFor.java");
  }

  public void testParameterJavaDoc1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDoc2() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDoc3() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDocBeforeVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testIncompleteVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, true, false);
  }

  public void testIncorrectScope() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, true, false);
  }

  public void testExpectedType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testRemoveParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterAfterVariable() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterInHierarchy() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testVarargs1() {   // IDEADEV-33555
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testUseInInnerClass() {
    doTestThroughHandler();
  }

  public void testLocalVarSelection() {
    doTestThroughHandler();
  }

  public void testGenerateDelegate() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  public void testGenerateDelegateRemoveParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, true);
  }

  public void testGenerateDelegateNoArgs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  public void testEnums() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testGetterQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testArrayInitializer() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  public void testIncompleteEnumDefinition() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false,
           "Incomplete call(Root()): 2 parameters expected but only 0 found\n" +
           "Incomplete call(Root()): expected to delete the 1 parameter but only 0 parameters found");
  }

  public void testStaticFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testGenerateDelegateInSuperClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  public void testGenerateDelegateInSuperInterface() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  public void testReplaceAllAndDeleteUnused() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDiamond2Raw() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDiamondOccurrences() {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testPreserveDiamondOccurrences() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSubstituteTypeParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSubstituteTypeParamsInInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDelegateWithVarargs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, true);
  }

  public void testSelfReference() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSelfReferenceVarargs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSelfReferenceVarargs1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSimplifiedResultedType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testPackageReferenceShouldBeIgnored() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testConflictingNameWithParameterToDelete() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  private void doTestThroughHandler() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
      enabled = myEditor.getSettings().isVariableInplaceRenameEnabled();
      myEditor.getSettings().setVariableInplaceRenameEnabled(false);
      new IntroduceParameterHandler().invoke(getProject(), myEditor, myFile, DataContext.EMPTY_CONTEXT);
      checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  public void testEnclosingWithParamDeletion() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(true, 0, "anObject", false, true, true, false, 1, false);
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  public void testCodeDuplicates() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(true, 0, "anObject", false, true, true, false, 0, true);
    UIUtil.dispatchAllInvocationEvents();
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  public void testCodeDuplicatesFromConstructor() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(true, 0, "anObject", false, true, true, false, 0, true);
    UIUtil.dispatchAllInvocationEvents();
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  public void testTypeAnnotation() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean searchForSuper,
                      boolean declareFinal,
                      boolean generateDelegate) {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, generateDelegate, null);
  }

  private void doTest(int replaceFieldsWithGetters,
                      boolean removeUnusedParameters,
                      boolean searchForSuper,
                      boolean declareFinal,
                      boolean generateDelegate,
                      String conflict) {
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
      enabled = myEditor.getSettings().isVariableInplaceRenameEnabled();
      myEditor.getSettings().setVariableInplaceRenameEnabled(false);
      perform(true, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters, generateDelegate);
      checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
      if (conflict != null) {
        fail("Conflict expected");
      }
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflict == null) {
        throw e;
      }
      assertEquals(conflict, e.getMessage());
    } finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private static boolean perform(boolean replaceAllOccurrences,
                                 int replaceFieldsWithGetters,
                                 @NonNls String parameterName,
                                 boolean searchForSuper,
                                 boolean declareFinal,
                                 boolean removeUnusedParameters,
                                 boolean generateDelegate) {
    return perform(
      replaceAllOccurrences, replaceFieldsWithGetters, parameterName, searchForSuper, declareFinal,
      removeUnusedParameters, generateDelegate, 0, false
    );
  }

  private static boolean perform(boolean replaceAllOccurrences,
                                 int replaceFieldsWithGetters,
                                 @NonNls String parameterName,
                                 boolean searchForSuper,
                                 boolean declareFinal,
                                 boolean removeUnusedParameters,
                                 boolean generateDelegate,
                                 int enclosingLevel,
                                 final boolean replaceDuplicates) {
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn.processElementToWorkOn(myEditor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, getProject(),
                                           new ElementToWorkOn.ElementsProcessor<ElementToWorkOn>() {
                                             @Override
                                             public boolean accept(ElementToWorkOn el) {
                                               return true;
                                             }

                                             @Override
                                             public void pass(final ElementToWorkOn e) {
                                               if (e != null) {
                                                 elementToWorkOn[0] = e;
                                               }
                                             }
                                           });

    final PsiExpression expr = elementToWorkOn[0].getExpression();
    final PsiLocalVariable localVar = elementToWorkOn[0].getLocalVariable();

    PsiElement context = expr == null ? localVar : expr;
    PsiMethod method = Util.getContainingMethod(context);
    if (method == null) return false;

    final List<PsiMethod> methods = com.intellij.refactoring.introduceParameter.IntroduceParameterHandler.getEnclosingMethods(method);
    assertTrue(methods.size() > enclosingLevel);
    method = methods.get(enclosingLevel);

    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      methodToSearchFor = method.findDeepestSuperMethods()[0];
    }
    else {
      methodToSearchFor = method;
    }
    PsiExpression[] occurrences;
    PsiExpression initializer;
    if (expr == null) {
      initializer = localVar.getInitializer();
      assertNotNull(initializer);
      occurrences = CodeInsightUtil.findReferenceExpressions(method, localVar);
    }
    else {
      initializer = expr;
      occurrences = new ExpressionOccurrenceManager(expr, method, null).findExpressionOccurrences();
    }
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer, occurrences)
                                                              : new TIntArrayList();
    IntroduceParameterProcessor processor = new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, initializer, expr, localVar, true, parameterName, replaceAllOccurrences,
      replaceFieldsWithGetters, declareFinal, generateDelegate, null, parametersToRemove
    ) {
      @Override
      protected boolean isReplaceDuplicates() {
        return replaceDuplicates;
      }
    };
    PsiType initializerType = initializer.getType();
    if (initializerType != null && initializerType != PsiType.NULL) {
      PsiExpression lambda = AnonymousCanBeLambdaInspection.replaceAnonymousWithLambda(initializer, initializerType);
      if (lambda != null) {
        processor.setParameterInitializer(lambda);
      }
    }
    processor.run();

    myEditor.getSelectionModel().removeSelection();
    return true;
  }

  private static void performForLocal(boolean searchForSuper,
                                      boolean removeLocalVariable,
                                      boolean replaceAllOccurrences,
                                      boolean declareFinal,
                                      final boolean removeUnusedParameters) {
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiElement element = ObjectUtils.assertNotNull(myFile.findElementAt(offset)).getParent();
    assertTrue(element instanceof PsiLocalVariable);
    PsiMethod method = Util.getContainingMethod(element);
    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      PsiMethod[] deepestSuperMethods = method.findDeepestSuperMethods();
      methodToSearchFor = deepestSuperMethods.length > 0 ? deepestSuperMethods[0] : method;
    }
    else {
      methodToSearchFor = method;
    }
    assertNotNull(method);
    assertNotNull(methodToSearchFor);
    final PsiLocalVariable localVariable = (PsiLocalVariable)element;
    final PsiExpression parameterInitializer = localVariable.getInitializer();
    assertNotNull(parameterInitializer);
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, parameterInitializer, null)
                                                              : new TIntArrayList();

    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurrences, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, false, null, parametersToRemove
    ).run();
  }
}
