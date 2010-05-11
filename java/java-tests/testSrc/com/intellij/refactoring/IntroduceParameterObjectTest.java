/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.introduceparameterobject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.util.Function;
import com.intellij.util.VisibilityUtil;

public class IntroduceParameterObjectTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/introduceParameterObject/";
  }
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(false, false);
  }

  private void doTest(final boolean delegate, final boolean createInner) throws Exception {
    doTest(delegate, createInner, new Function<PsiMethod, ParameterTablePanel.VariableData[]>() {
      public ParameterTablePanel.VariableData[] fun(PsiMethod psiMethod) {
        return generateParams(psiMethod);
      }
    });
  }

  private void doTest(final boolean delegate,
                      final boolean createInner,
                      final Function<PsiMethod, ParameterTablePanel.VariableData[]> function) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));

        assertNotNull("Class Test not found", aClass);

        final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        final ParameterTablePanel.VariableData[] datas = function.fun(method);

        IntroduceParameterObjectProcessor processor = new IntroduceParameterObjectProcessor("Param", "", method, datas, delegate, false,
                                                                                            createInner, null, false);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  private static ParameterTablePanel.VariableData[] generateParams(final PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();

    final ParameterTablePanel.VariableData[] datas = new ParameterTablePanel.VariableData[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      datas[i] = new ParameterTablePanel.VariableData(parameter);
      datas[i].name = parameter.getName();
      datas[i].passAsParameter = true;
    }
    return datas;
  }

  public void testInnerClass() throws Exception {
    doTest(false, true);
  }

  public void testInnerClassInInterface() throws Exception {
    doTest(false, true);
  }

  public void testCopyJavadoc() throws Exception {
    doTest(false, true);
  }

  public void testUsedInnerClass() throws Exception {
    doTest(false, true);
  }

  public void testPrimitive() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testIncrement() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

  public void testLhassignment() throws Exception {
    doTest();
  }

  public void testSuperCalls() throws Exception {
    doTest();
  }

  public void testTypeParameters() throws Exception {
    doTest();
  }

  public void testTypeParametersWithChosenSubtype() throws Exception {
    doTest(false, true, new Function<PsiMethod, ParameterTablePanel.VariableData[]>() {
      public ParameterTablePanel.VariableData[] fun(PsiMethod psiMethod) {
        final PsiParameter parameter = psiMethod.getParameterList().getParameters()[0];
        final PsiClass collectionClass = JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION);
        final ParameterTablePanel.VariableData variableData =
          new ParameterTablePanel.VariableData(parameter, JavaPsiFacade.getElementFactory(getProject()).createType(collectionClass));
        variableData.name = parameter.getName();
        variableData.passAsParameter = true;
        return new ParameterTablePanel.VariableData[]{variableData};
      }
    });
  }

  public void testMultipleTypeParameters() throws Exception {
    doTest();
  }

  public void testDelegate() throws Exception {
    doTest(true, false);
  }

  private void doTestExistingClass(final String existingClassName, final String existingClassPackage, final boolean generateAccessors) throws Exception {
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, null);
  }

  private void doTestExistingClass(final String existingClassName, final String existingClassPackage, final boolean generateAccessors,
                                   final String newVisibility) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));
        if (aClass == null) {
          aClass = myJavaFacade.findClass("p2.Test", GlobalSearchScope.projectScope(getProject()));
        }
        assertNotNull("Class Test not found", aClass);

        final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        IntroduceParameterObjectProcessor processor = new IntroduceParameterObjectProcessor(existingClassName, existingClassPackage, method,
                                                                                            generateParams(method), false, true,
                                                                                            false, newVisibility, generateAccessors);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testIntegerWrapper() throws Exception {
    doTestExistingClass("Integer", "java.lang", false);
  }

  public void testIntegerIncremental() throws Exception {
    checkExceptionThrown("Integer", "java.lang", "Cannot perform the refactoring.\n" +
                                                 "Setters for the following fields are required:\n" +
                                                 "value.\n");
  }

  private void checkExceptionThrown(String existingClassName, String existingClassPackage, String exceptionMessage) throws Exception {
    try {
      doTestExistingClass(existingClassName, existingClassPackage, false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(exceptionMessage, e.getMessage());
      return;
    }
    fail("Conflict was not found");
  }

  public void testGenerateGetterSetterForExistingBean() throws Exception {
    doTestExistingClass("Param", "", true);
  }

  public void testExistingBeanVisibility() throws Exception {
    doTestExistingClass("Param", "p", false, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testExistingBeanIfNoGeneration() throws Exception {
    checkExceptionThrown("Param", "", "Cannot perform the refactoring.\n" + "Setters for the following fields are required:\n" + "i.\n");
  }

  public void testParamNameConflict() throws Exception {
    doTestExistingClass("Param", "", true);
  }


  public void testExistentBean() throws Exception {
    doTestExistingClass("Param", "", false);
  }

  public void testWrongBean() throws Exception {
    checkExceptionThrown("Param", "", "Cannot perform the refactoring.\n" + "Getters for the following fields are required:\n" + "i.\n");
  }
}
