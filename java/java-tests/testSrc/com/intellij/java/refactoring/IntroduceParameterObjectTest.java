// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.util.Function;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IntroduceParameterObjectTest extends LightMultiFileTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceParameterObject/";
  }

  private void doTest() {
    doTest(false, false);
  }

  private void doTest(final boolean delegate, final boolean createInner) {
    doTest(delegate, createInner, IntroduceParameterObjectTest::generateParams);
  }

  private void doTest(final boolean delegate,
                      final boolean createInner,
                      final Function<PsiMethod, ParameterInfoImpl[]> function) {
    doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");
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
      datas[i] = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(parameter.getType());
    }
    return datas;
  }

  public void testInnerClass() {
    doTest(false, true);
  }

  public void testInnerClassInInterface() {
    doTest(false, true);
  }

  public void testCopyJavadoc() {
    doTest(false, true);
  }

  public void testUsedInnerClass() {
    doTest(false, true);
  }

  public void testPrimitive() {
    doTest();
  }

  public void testVarargs() {
    doTest();
  }

  public void testIncrement() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testLhassignment() {
    doTest();
  }

  public void testSuperCalls() {
    doTest();
  }

  public void testTypeParameters() {
    doTest();
  }

  public void testTypeParametersWithSubstitution() {
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

  public void testSameTypeAndVarargs() {
    doTest(false, false, method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();

      final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length - 1];
      for (int i = 0; i < parameters.length - 1; i++) {
        PsiParameter parameter = parameters[i];
        datas[i] = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(parameter.getType());
      }
      return datas;
    });
  }

  public void testCopyJavadoc1() {
    doTest(false, true, method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();

      final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length - 1];
      for (int i = 0; i < parameters.length - 1; i++) {
        PsiParameter parameter = parameters[i];
        datas[i] = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(parameter.getType());
      }
      return datas;
    });
  }

  public void testIncludeOneParameter() {
    doTestExistingClass("Param", "", false, "public", method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiParameter parameter = parameters[1];
      return new ParameterInfoImpl[]{ParameterInfoImpl.create(1).withName(parameter.getName()).withType(parameter.getType())};
    });
  }

  public void testTypeParametersWithChosenSubtype() {
    doTest(false, true, psiMethod -> {
      final PsiParameter parameter = psiMethod.getParameterList().getParameters()[0];
      final PsiClass collectionClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_COLLECTION);
      final ParameterInfoImpl variableData = ParameterInfoImpl.create(0)
        .withName(parameter.getName())
        .withType(JavaPsiFacade.getElementFactory(getProject()).createType(collectionClass));
      return new ParameterInfoImpl[]{variableData};
    });
  }

  public void testMultipleTypeParameters() {
    doTest();
  }

  public void testDelegate() {
    doTest(true, false);
  }

  private void doTestExistingClass(@NotNull String existingClassName, final String existingClassPackage, final boolean generateAccessors) {
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, null);
  }

  private void doTestExistingClass(@NotNull String existingClassName, final String existingClassPackage, final boolean generateAccessors,
                                   final String newVisibility) {
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, newVisibility,
                        IntroduceParameterObjectTest::generateParams);
  }

  private void doTestExistingClass(@NotNull String existingClassName,
                                   final String existingClassPackage,
                                   final boolean generateAccessors,
                                   final String newVisibility,
                                   final Function<PsiMethod, ParameterInfoImpl[]> function) {
    doTest(() -> {
      PsiClass aClass = myFixture.getJavaFacade().findClass("Test", GlobalSearchScope.projectScope(getProject()));
      if (aClass == null) {
        aClass = myFixture.getJavaFacade().findClass("p2.Test", GlobalSearchScope.projectScope(getProject()));
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

  public void testIntegerWrapper() {
    doTestExistingClass("Integer", "java.lang", false);
  }

  public void testIntegerIncremental() {
    checkExceptionThrown("Integer", "java.lang", "Setter for field 'value' is required");
  }

  private void checkExceptionThrown(@NotNull String existingClassName, String existingClassPackage, String exceptionMessage) {
    try {
      doTestExistingClass(existingClassName, existingClassPackage, false);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(exceptionMessage, e.getMessage());
      return;
    }
    fail("Conflict was not found");
  }

  public void testGenerateGetterSetterForExistingBean() {
    doTestExistingClass("Param", "", true);
  }

  public void testExistingBeanVisibility() {
    doTestExistingClass("Param", "p", false, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  public void testExistingBeanIfNoGeneration() {
    checkExceptionThrown("Param", "", "Setter for field 'i' is required");
  }

  public void testParamNameConflict() {
    doTestExistingClass("Param", "", true);
  }


  public void testExistentBean() {
    doTestExistingClass("Param", "", false);
  }

  public void testExistingWithAnotherFieldNames() {
    doTestExistingClass("Param", "", true);
  }

  public void testWrongBean() {
    checkExceptionThrown("Param", "", "Getter for field 'i' is required");
  }
}
