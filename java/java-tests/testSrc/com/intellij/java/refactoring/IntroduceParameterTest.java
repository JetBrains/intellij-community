// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.CommonJavaRefactoringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

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

  public void testVarargMethodStricktlyCalled() {
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

  public void testRecordGetterImpl() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, () -> 
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, 
             "method <b><code>R.name()</code></b> will no longer be record component <b><code>name</code></b> getter"));
  }

  public void testCanonicalConstructor() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, () -> 
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, 
             "Constructor will no longer be canonical"));
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

  public void testRemoveParameterInHierarchy1() {
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

  public void testWrapVarargsParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  private void doTestThroughHandler() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
      enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
      getEditor().getSettings().setVariableInplaceRenameEnabled(false);
      new IntroduceParameterHandler().invoke(getProject(), getEditor(), getFile(), DataContext.EMPTY_CONTEXT);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); // let all the invokeLater to pass through
      checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  public void testEnclosingWithParamDeletion() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 1, false);
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  public void testCodeDuplicates() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 0, true);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  public void testCodeDuplicatesFromConstructor() {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 0, true);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
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
      enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
      getEditor().getSettings().setVariableInplaceRenameEnabled(false);
      perform(IntroduceVariableBase.JavaReplaceChoice.ALL, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters, generateDelegate);
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
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private boolean perform(IntroduceVariableBase.JavaReplaceChoice replaceAllOccurrences,
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

  private boolean perform(IntroduceVariableBase.JavaReplaceChoice replaceAllOccurrences,
                          int replaceFieldsWithGetters,
                          @NonNls String parameterName,
                          boolean searchForSuper,
                          boolean declareFinal,
                          boolean removeUnusedParameters,
                          boolean generateDelegate,
                          int enclosingLevel,
                          final boolean replaceDuplicates) {
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn.processElementToWorkOn(getEditor(), getFile(), "INtr param", HelpID.INTRODUCE_PARAMETER, getProject(),
                                           new ElementToWorkOn.ElementsProcessor<>() {
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

    final List<PsiMethod> methods = CommonJavaRefactoringUtil.getEnclosingMethods(method);
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
    IntArrayList parametersToRemove = removeUnusedParameters ? new IntArrayList(Util.findParametersToRemove(method, initializer, occurrences))
                                                             : new IntArrayList();
    IntroduceParameterProcessor processor = new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, initializer, expr, localVar, true, parameterName, replaceAllOccurrences,
      replaceFieldsWithGetters, declareFinal, generateDelegate, false, null, parametersToRemove
    ) {
      @Override
      protected boolean isReplaceDuplicates() {
        return replaceDuplicates;
      }
    };
    PsiType initializerType = initializer.getType();
    if (initializerType != null && initializerType != PsiTypes.nullType()) {
      PsiExpression lambda = AnonymousCanBeLambdaInspection.replaceAnonymousWithLambda(initializer, initializerType);
      if (lambda != null) {
        processor.setParameterInitializer(lambda);
      }
    }
    processor.run();

    getEditor().getSelectionModel().removeSelection();
    return true;
  }

  private void performForLocal(boolean searchForSuper,
                               boolean removeLocalVariable,
                               boolean replaceAllOccurrences,
                               boolean declareFinal,
                               final boolean removeUnusedParameters) {
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement element = Objects.requireNonNull(getFile().findElementAt(offset)).getParent();
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
    IntArrayList parametersToRemove = removeUnusedParameters ? new IntArrayList(Util.findParametersToRemove(method, parameterInitializer, null))
                                                              : new IntArrayList();

    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurrences ? IntroduceVariableBase.JavaReplaceChoice.ALL : IntroduceVariableBase.JavaReplaceChoice.NO, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, false, false, null, parametersToRemove
    ).run();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); // let all the invokeLater to pass through
  }
}
