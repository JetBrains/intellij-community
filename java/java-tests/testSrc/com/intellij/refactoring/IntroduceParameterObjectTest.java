/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.util.Function;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IntroduceParameterObjectTest extends MultiFileTestCase{
  @NotNull
  @Override
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
    doTest(delegate, createInner, IntroduceParameterObjectTest::generateParams);
  }

  private void doTest(final boolean delegate,
                      final boolean createInner,
                      final Function<PsiMethod, ParameterInfoImpl[]> function) throws Exception {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));

      assertNotNull("Class Test not found", aClass);

      final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
      final ParameterInfoImpl[] datas = function.fun(method);

      final JavaIntroduceParameterObjectClassDescriptor classDescriptor =
        new JavaIntroduceParameterObjectClassDescriptor("Param", "", null, false, createInner, null, datas, method, false);
      final List<ParameterInfoImpl> parameters = new JavaMethodDescriptor(method).getParameters();
      IntroduceParameterObjectProcessor processor =
        new IntroduceParameterObjectProcessor<>(
          method, classDescriptor,
          parameters,
          delegate);
      processor.run();
    });
  }

  private static ParameterInfoImpl[] generateParams(final PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();

    final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      datas[i] = new ParameterInfoImpl(i, parameter.getName(), parameter.getType());
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

  public void testTypeParametersWithSubstitution() throws Exception {
    final LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel oldLevel = projectExtension.getLanguageLevel();
    try {
      projectExtension.setLanguageLevel(LanguageLevel.HIGHEST);
      doTest();
    }
    finally {
      projectExtension.setLanguageLevel(oldLevel);
    }
  }

  public void testSameTypeAndVarargs() throws Exception {
    doTest(false, false, method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();

      final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length - 1];
      for (int i = 0; i < parameters.length - 1; i++) {
        PsiParameter parameter = parameters[i];
        datas[i] = new ParameterInfoImpl(i, parameter.getName(), parameter.getType());
      }
      return datas;
    });
  }

  public void testCopyJavadoc1() throws Exception {
    doTest(false, true, method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();

      final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length - 1];
      for (int i = 0; i < parameters.length - 1; i++) {
        PsiParameter parameter = parameters[i];
        datas[i] = new ParameterInfoImpl(i, parameter.getName(), parameter.getType());
      }
      return datas;
    });
  }

  public void testIncludeOneParameter() throws Exception {
    doTestExistingClass("Param", "", false, "public", method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiParameter parameter = parameters[1];
      return new ParameterInfoImpl[]{new ParameterInfoImpl(1, parameter.getName(), parameter.getType())};
    });
  }

  public void testTypeParametersWithChosenSubtype() throws Exception {
    doTest(false, true, psiMethod -> {
      final PsiParameter parameter = psiMethod.getParameterList().getParameters()[0];
      final PsiClass collectionClass = getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_COLLECTION);
      final ParameterInfoImpl variableData =
        new ParameterInfoImpl(0, parameter.getName(), JavaPsiFacade.getElementFactory(getProject()).createType(collectionClass));
      return new ParameterInfoImpl[]{variableData};
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
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, newVisibility,
                        IntroduceParameterObjectTest::generateParams);
  }

  private void doTestExistingClass(final String existingClassName,
                                   final String existingClassPackage,
                                   final boolean generateAccessors,
                                   final String newVisibility,
                                   final Function<PsiMethod, ParameterInfoImpl[]> function) throws Exception {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));
      if (aClass == null) {
        aClass = myJavaFacade.findClass("p2.Test", GlobalSearchScope.projectScope(getProject()));
      }
      assertNotNull("Class Test not found", aClass);

      final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
      final ParameterInfoImpl[] mergedParams = function.fun(method);
      final JavaIntroduceParameterObjectClassDescriptor classDescriptor =
        new JavaIntroduceParameterObjectClassDescriptor(existingClassName, existingClassPackage, null, true, false, newVisibility,
                                                        mergedParams, method, generateAccessors);
      final List<ParameterInfoImpl> parameters = new JavaMethodDescriptor(method).getParameters();
      IntroduceParameterObjectProcessor processor =
        new IntroduceParameterObjectProcessor<>(
          method, classDescriptor,
          parameters,
          false);
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  public void testIntegerWrapper() throws Exception {
    doTestExistingClass("Integer", "java.lang", false);
  }

  public void testIntegerIncremental() throws Exception {
    checkExceptionThrown("Integer", "java.lang", "Setter for field 'value' is required");
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
    checkExceptionThrown("Param", "", "Setter for field 'i' is required");
  }

  public void testParamNameConflict() throws Exception {
    doTestExistingClass("Param", "", true);
  }


  public void testExistentBean() throws Exception {
    doTestExistingClass("Param", "", false);
  }

  public void testExistingWithAnotherFieldNames() throws Exception {
    doTestExistingClass("Param", "", true);
  }

  public void testWrongBean() throws Exception {
    checkExceptionThrown("Param", "", "Getter for field 'i' is required");
  }
}
