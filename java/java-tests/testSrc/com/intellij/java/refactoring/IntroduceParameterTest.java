// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonJavaRefactoringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/testData")
@TestApplication
public class IntroduceParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testNoUsages() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testSimpleUsage() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testMethodWithoutParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testParameterSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testThisSubstitution() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testThisSubstitutionInQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false,
           "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. " +
           "Value for introduced parameter in that method call will be incorrect.");
  }

  @Test
  public void testRemoveOverrideFromDelegated() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  @Test
  public void testFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testMethodAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testStaticFieldAccess() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false, false);
  }

  @Test
  public void testFieldWithInaccessibleGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testWeirdQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testSuperInExpression() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  @Test
  public void testNull() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testWeirdQualifierAndParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testImplicitSuperCall() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testImplicitDefaultConstructor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testLocalVarDeclaration() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testInternalSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testQualifiedNew() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testAnonymousClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testCollapseToLambda() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testExpandMethodReference() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testBareRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  @Test
  public void testNewWithRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  @Test
  public void testVarargMethodStricktlyCalled() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  @Test
  public void testMethodCallRefToVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  @Test
  public void testSuperWithSideEffect() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false,
           "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  @Test
  public void testConflictingField() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false, false);
  }

  @Test
  public void testRecordGetterImpl() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, () -> 
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, 
             "method <b><code>R.name()</code></b> will no longer be record component <b><code>name</code></b> getter"));
  }

  @Test
  public void testCanonicalConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, () -> 
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, 
             "Constructor will no longer be canonical"));
  }

  @Test
  public void testParameterInFor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/beforeParameterInFor.java");
    myFixture.runInCommand(() -> {
      performForLocal(true, true, true, false, false);
      myFixture.checkResultByFile("/refactoring/introduceParameter/afterParameterInFor.java");
    });
  }

  @Test
  public void testParameterJavaDoc1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  @Test
  public void testParameterJavaDoc2() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  @Test
  public void testParameterJavaDoc3() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  @Test
  public void testParameterJavaDocBeforeVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  @Test
  public void testIncompleteVararg() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, true, false);
  }

  @Test
  public void testIncorrectScope() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, true, false);
  }

  @Test
  public void testExpectedType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  @Test
  public void testRemoveParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testRemoveParameterAfterVariable() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testRemoveParameterInHierarchy() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testRemoveParameterInHierarchy1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testRemoveParameterWithJavadoc() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testVarargs() {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testVarargs1() {   // IDEADEV-33555
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  @Test
  public void testUseInInnerClass() {
    doTestThroughHandler();
  }

  @Test
  public void testLocalVarSelection() {
    doTestThroughHandler();
  }

  @Test
  public void testGenerateDelegate() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  @Test
  public void testGenerateDelegateRemoveParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, true);
  }

  @Test
  public void testGenerateDelegateNoArgs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  @Test
  public void testEnums() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testMethodUsageInThisMethodInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  @Test
  public void testGetterQualifier() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  @Test
  public void testArrayInitializer() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  @Test
  public void testIncompleteEnumDefinition() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false,
           "Incomplete call(Root()): 2 parameters expected but only 0 found\n" +
           "Incomplete call(Root()): expected to delete the 1 parameter but only 0 parameters found");
  }

  @Test
  public void testStaticFieldWithGetterReplacement() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testGenerateDelegateInSuperClass() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  @Test
  public void testGenerateDelegateInSuperInterface() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  @Test
  public void testReplaceAllAndDeleteUnused() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testDiamond2Raw() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testDiamondOccurrences() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, myFixture.getTestRootDisposable());
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testPreserveDiamondOccurrences() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testSubstituteTypeParams() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testSubstituteTypeParamsInInheritor() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testDelegateWithVarargs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, true);
  }

  @Test
  public void testSelfReference() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testSelfReferenceVarargs() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testSelfReferenceVarargs1() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testSimplifiedResultedType() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testPackageReferenceShouldBeIgnored() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testConflictingNameWithParameterToDelete() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  @Test
  public void testWrapVarargsParameter() {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  private void doTestThroughHandler() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean enabled = true;
      try {
        myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
        enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(false);
        new IntroduceParameterHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), DataContext.EMPTY_CONTEXT);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); // let all the invokeLater to pass through
        myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
      }
      finally {
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      }
    });
  }

  @Test
  public void testEnclosingWithParamDeletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 1, false);
      myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  public void testCodeDuplicates() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 0, true);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  public void testCodeDuplicatesFromConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "anObject", false, true, true, false, 0, true);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
    });
  }
  
  @Test
  public void testIgnoreDuplicatesInConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      perform(IntroduceVariableBase.JavaReplaceChoice.ALL, 0, "i", false, false, true, false, 0, true);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      boolean enabled = true;
      try {
        myFixture.configureByFile("/refactoring/introduceParameter/before" + myFixture.getTestName(false) + ".java");
        enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(false);
        perform(IntroduceVariableBase.JavaReplaceChoice.ALL, replaceFieldsWithGetters, "anObject", searchForSuper, declareFinal, removeUnusedParameters, generateDelegate);
        myFixture.checkResultByFile("/refactoring/introduceParameter/after" + myFixture.getTestName(false) + ".java");
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
        myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      }
    });
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn.processElementToWorkOn(myFixture.getEditor(), myFixture.getFile(), "INtr param", HelpID.INTRODUCE_PARAMETER, myFixture.getProject(),
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
      myFixture.getProject(), method, methodToSearchFor, initializer, expr, localVar, true, parameterName, replaceAllOccurrences,
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

    myFixture.getEditor().getSelectionModel().removeSelection();
    return true;
  }

  private void performForLocal(boolean searchForSuper,
                               boolean removeLocalVariable,
                               boolean replaceAllOccurrences,
                               boolean declareFinal,
                               boolean removeUnusedParameters) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiElement element = Objects.requireNonNull(myFixture.getFile().findElementAt(offset)).getParent();
    assertInstanceOf(PsiLocalVariable.class, element);
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
      myFixture.getProject(), method, methodToSearchFor, parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurrences ? IntroduceVariableBase.JavaReplaceChoice.ALL : IntroduceVariableBase.JavaReplaceChoice.NO, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, false, false, null, parametersToRemove
    ).run();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue(); // let all the invokeLater to pass through
  }
}
