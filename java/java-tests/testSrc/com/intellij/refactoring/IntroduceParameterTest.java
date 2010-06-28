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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceParameterTest extends LightCodeInsightTestCase {
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
    try {
      configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, false, false, false, "field <b><code>Test.i</code></b> is not accesible from method <b><code>XTest.n()</code></b>. Value for introduced parameter in that method call will be incorrect.");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
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
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, false, false, false, false, "Parameter initializer contains <b><code>super</code></b>, but not all calls to method are in its class.");
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

  public void testIncompleteEnumDefinition() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, true, false, false, false, "Incomplete call(Root()): 2 parameters expected but only 0 found\n" +
                                                                                                      "Incomplete call(Root()): expected to delete the 1 parameter but only 0 parameters found");
  }

  public void testStaticFieldWithGetterReplacement() throws Exception {
    doTest(IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL, true, false, true, false);
  }

  private void doTestThroughHandler() throws Exception {
    configureByFile("/refactoring/introduceParameter/before" + getTestName(false) + ".java");
    new IntroduceParameterHandler().invoke(getProject(), myEditor, myFile, new DataContext() {
      @Nullable
      public Object getData(@NonNls final String dataId) {
        return null;
      }
    });
    checkResultByFile("/refactoring/introduceParameter/after" + getTestName(false) + ".java");
  }

  private static boolean perform(boolean replaceAllOccurences,
                                 int replaceFieldsWithGetters,
                                 @NonNls String parameterName,
                                 boolean searchForSuper,
                                 boolean declareFinal,
                                 final boolean removeUnusedParameters,
                                 final boolean generateDelegate) {
    int startOffset = myEditor.getSelectionModel().getSelectionStart();
    int endOffset = myEditor.getSelectionModel().getSelectionEnd();

    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFile, startOffset, endOffset);

    PsiLocalVariable localVariable = null;
    if (expr == null) {
      PsiElement element = CodeInsightUtil.findElementInRange(myFile, startOffset, endOffset, PsiElement.class);
      localVariable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
      if (localVariable == null) {
        return false;
      }
    }
    PsiElement context = expr == null ? localVariable : expr;
    PsiMethod method = Util.getContainingMethod(context);
    if (method == null) return false;

    final PsiMethod methodToSearchFor;
    if (searchForSuper) {
      methodToSearchFor = method.findDeepestSuperMethod();
    }
    else {
      methodToSearchFor = method;
    }
    PsiExpression initializer = expr == null ? localVariable.getInitializer() : expr;
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, initializer) : new TIntArrayList();
    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor, initializer, expr, localVariable, true, parameterName, replaceAllOccurences,
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
    TIntArrayList parametersToRemove = removeUnusedParameters ? Util.findParametersToRemove(method, parameterInitializer) : new TIntArrayList();

    new IntroduceParameterProcessor(
      getProject(), method, methodToSearchFor,
      parameterInitializer, null, localVariable, removeLocalVariable,
      localVariable.getName(), replaceAllOccurrences,
      IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE,
      declareFinal, false, null, parametersToRemove).run();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}
