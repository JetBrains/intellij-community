/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.05.2002
 * Time: 13:59:01
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.testFramework.TestDataPath;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceParameterTest extends LightRefactoringTestCase  {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal,
                      final boolean generateDelegate) throws Exception {
    doTest(replaceFieldsWithGetters, removeUnusedParameters, searchForSuper, declareFinal, generateDelegate, null);
  }

  private void doTest(int replaceFieldsWithGetters, boolean removeUnusedParameters, boolean searchForSuper, boolean declareFinal, final boolean generateDelegate,
                      String conflict) throws Exception {
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

  public void testNoUsages() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testSimpleUsage() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodWithoutParams() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testParameterSubstitution() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testThisSubstitution() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testThisSubstitutionInQualifier() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false, "field <b><code>Test.i</code></b> is not accessible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
  }

  public void testFieldAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testStaticFieldAccess() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testFieldWithGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, false, false, false, false);
  }

  public void testFieldWithInaccessibleGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testWeirdQualifier() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testSuperInExpression() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testNull() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testWeirdQualifierAndParameter() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testImplicitSuperCall() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testImplicitDefaultConstructor() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testLocalVarDeclaration() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testInternalSideEffect() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testQualifiedNew() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testAnonymousClass() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testSuperWithSideEffect() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class");
  }

  public void testConflictingField() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, false, false);
  }

  public void testParameterInFor() throws Exception {
    configureByFile("/refactoring/introduceParameter/beforeParameterInFor.java");
    performForLocal(true, true, true, false, false);
    checkResultByFile("/refactoring/introduceParameter/afterParameterInFor.java");
  }

  public void testParameterJavaDoc1() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDoc2() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDoc3() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testParameterJavaDocBeforeVararg() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testIncompleteVararg() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, true, false, "Incomplete call(method()): 2 parameters expected but only 0 found\n" +
                                                                                                             "Incomplete call(method()): expected to delete the 0 parameter but only 0 parameters found");
  }

  public void testIncorrectScope() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, true, true, false);
  }

  public void testExpectedType() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, true, false);
  }

  public void testRemoveParameter() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterAfterVariable() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterInHierarchy() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testRemoveParameterWithJavadoc() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testVarargs() throws Exception {   // IDEADEV-16828
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testVarargs1() throws Exception {   // IDEADEV-33555
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false);
  }

  public void testUseInInnerClass() throws Exception {
    doTestThroughHandler();
  }

  public void testLocalVarSelection() throws Exception {
    doTestThroughHandler();
  }

  public void testGenerateDelegate() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  public void testGenerateDelegateRemoveParameter() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, true);
  }

  public void testGenerateDelegateNoArgs() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, true);
  }

  public void testEnums() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testMethodUsageInThisMethodInheritor() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false);
  }

  public void testGetterQualifier() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false);
  }

  public void testArrayInitializer() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, true, false, false, false);
  }

  public void testIncompleteEnumDefinition() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false, "Incomplete call(Root()): 2 parameters expected but only 0 found\n" +
                                                                                                      "Incomplete call(Root()): expected to delete the 1 parameter but only 0 parameters found");
  }

  public void testStaticFieldWithGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testGenerateDelegateInSuperClass() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  public void testGenerateDelegateInSuperInterface() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, true, true, true);
  }

  public void testReplaceAllAndDeleteUnused() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDiamond2Raw() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDiamondOccurrences() throws Exception {
    final LanguageLevel oldLevel = getLanguageLevel();
    try {
      setLanguageLevel(LanguageLevel.JDK_1_7);
      doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
    }
    finally {
      setLanguageLevel(oldLevel);
    }
  }

  public void testPreserveDiamondOccurrences() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSubstituteTypeParams() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testDelegateWithVarargs() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, true);
  }

  public void testSelfReference() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSelfReferenceVarargs() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }
  
  public void testSelfReferenceVarargs1() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testSimplifiedResultedType() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  public void testPackageReferenceShouldBeIgnored() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  private void doTestThroughHandler() throws Exception {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    boolean enabled = true;
    try {
      configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
      enabled = myEditor.getSettings().isVariableInplaceRenameEnabled();
      myEditor.getSettings().setVariableInplaceRenameEnabled(false);
      new IntroduceParameterHandler().invoke(getProject(), myEditor, myFile, new DataContext() {
        @Override
        @Nullable
        public Object getData(@NonNls final String dataId) {
          return null;
        }
      });
      checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
    }
    finally {
      myEditor.getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  public void testEnclosingWithParamDeletion() throws Exception {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    perform(true, 0, "anObject", false, true, true, false, 1);
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  private static boolean perform(boolean replaceAllOccurences,
                                 int replaceFieldsWithGetters,
                                 @NonNls String parameterName,
                                 boolean searchForSuper,
                                 boolean declareFinal,
                                 final boolean removeUnusedParameters,
                                 final boolean generateDelegate) {
    return perform(replaceAllOccurences, replaceFieldsWithGetters, parameterName, searchForSuper, declareFinal, removeUnusedParameters,
                   generateDelegate, 0);
  }

  private static boolean perform(boolean replaceAllOccurences,
                                 int replaceFieldsWithGetters,
                                 @NonNls String parameterName,
                                 boolean searchForSuper,
                                 boolean declareFinal,
                                 final boolean removeUnusedParameters,
                                 final boolean generateDelegate,
                                 int enclosingLevel) {
    final ElementToWorkOn[] elementToWorkOn = new ElementToWorkOn[1];
    ElementToWorkOn
          .processElementToWorkOn(myEditor, myFile, "INtr param", HelpID.INTRODUCE_PARAMETER, getProject(), new ElementToWorkOn.ElementsProcessor<ElementToWorkOn>() {
            @Override
            public boolean accept(ElementToWorkOn el) {
              return true;
            }

            @Override
            public void pass(final ElementToWorkOn e) {
              if (e == null) return;

              elementToWorkOn[0] = e;
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
      methodToSearchFor = method.findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method;
    }
    PsiExpression[] occurences;
    PsiExpression initializer;
    if (expr == null) {
      initializer = localVar.getInitializer();
      occurences = CodeInsightUtil.findReferenceExpressions(method, localVar);
    }
    else {
      initializer = expr;
      occurences = new ExpressionOccurrenceManager(expr, method, null).findExpressionOccurrences();
    }
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer, occurences) : new TIntArrayList();
    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, initializer, expr, localVar, true, parameterName, replaceAllOccurences,
      replaceFieldsWithGetters,
      declareFinal, generateDelegate, null, parametersToRemove).run();

    myEditor.getSelectionModel().removeSelection();
    return true;
  }

  private static void performForLocal(boolean searchForSuper, boolean removeLocalVariable, boolean replaceAllOccurrences, boolean declareFinal,
                                      final boolean removeUnusedParameters) {
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiElement element = myFile.findElementAt(offset).getParent();
    assertTrue(element instanceof PsiLocalVariable);
    PsiMethod method = Util.getContainingMethod(element);
    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
      methodToSearchFor = deepestSuperMethod != null ? deepestSuperMethod : method;
    }
    else {
      methodToSearchFor = method;
    }
    assertNotNull(method);
    assertNotNull(methodToSearchFor);
    final PsiLocalVariable localVariable = (PsiLocalVariable)element;
    final PsiExpression parameterInitializer = localVariable.getInitializer();
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, parameterInitializer, null) : new TIntArrayList();

    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor,
      parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurrences,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, false, null, parametersToRemove).run();
  }
}
