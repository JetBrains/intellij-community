// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.changeSignature.JavaMethodDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.JavaIntroduceParameterObjectClassDescriptor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class IntroduceParameterObjectTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceParameterObject/");

  private void doTest() {
    doTest(false, false);
  }

  private void doTest(boolean delegate, boolean createInner) {
    doTest(delegate, createInner, IntroduceParameterObjectTest::generateParams);
  }

  private void doTest(boolean delegate, boolean createInner, Function<PsiMethod, ParameterInfoImpl[]> function) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");
      final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
      final ParameterInfoImpl[] datas = function.apply(method);

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

  private static ParameterInfoImpl[] generateParams(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();

    final ParameterInfoImpl[] datas = new ParameterInfoImpl[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      datas[i] = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(parameter.getType());
    }
    return datas;
  }

  @Test
  public void testInnerClass() {
    doTest(false, true);
  }

  @Test
  public void testInnerClassInInterface() {
    doTest(false, true);
  }

  @Test
  public void testCopyJavadoc() {
    doTest(false, true);
  }

  @Test
  public void testUsedInnerClass() {
    doTest(false, true);
  }

  @Test
  public void testMultipleFieldsInnerClass() {
    doTest(false, true);
  }

  @Test
  public void testPrimitive() {
    doTest();
  }

  @Test
  public void testVarargs() {
    doTest();
  }

  @Test
  public void testIncrement() {
    doTest();
  }

  @Test
  public void testHierarchy() {
    doTest();
  }

  @Test
  public void testLhassignment() {
    doTest();
  }

  @Test
  public void testSuperCalls() {
    doTest();
  }

  @Test
  public void testTypeParameters() {
    doTest();
  }

  @Test
  public void testTypeParametersWithSubstitution() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final LanguageLevel oldLevel = IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.HIGHEST);
    try {
      doTest();
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), oldLevel);
    }
  }

  @Test
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

  @Test
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

  @Test
  public void testIncludeOneParameter() {
    doTestExistingClass("Param", "", false, "public", method -> {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiParameter parameter = parameters[1];
      return new ParameterInfoImpl[]{ParameterInfoImpl.create(1).withName(parameter.getName()).withType(parameter.getType())};
    });
  }

  @Test
  public void testTypeParametersWithChosenSubtype() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTest(false, true, psiMethod -> {
      final PsiParameter parameter = psiMethod.getParameterList().getParameters()[0];
      final PsiClass collectionClass = myFixture.findClass(CommonClassNames.JAVA_UTIL_COLLECTION);
      final ParameterInfoImpl variableData = ParameterInfoImpl.create(0)
        .withName(parameter.getName())
        .withType(JavaPsiFacade.getElementFactory(myFixture.getProject()).createType(collectionClass));
      return new ParameterInfoImpl[]{variableData};
    });
  }

  @Test
  public void testMultipleTypeParameters() {
    doTest();
  }

  @Test
  public void testDelegate() {
    doTest(true, false);
  }

  private void doTestExistingClass(@NotNull String existingClassName, String existingClassPackage, boolean generateAccessors) {
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, null);
  }

  private void doTestExistingClass(@NotNull String existingClassName, String existingClassPackage, boolean generateAccessors,
                                   String newVisibility) {
    doTestExistingClass(existingClassName, existingClassPackage, generateAccessors, newVisibility,
                        IntroduceParameterObjectTest::generateParams);
  }

  private void doTestExistingClass(@NotNull String existingClassName,
                                   String existingClassPackage,
                                   boolean generateAccessors,
                                   String newVisibility,
                                   Function<PsiMethod, ParameterInfoImpl[]> function) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.getJavaFacade().findClass("Test", GlobalSearchScope.projectScope(myFixture.getProject()));
      if (aClass == null) {
        aClass = myFixture.getJavaFacade().findClass("p2.Test", GlobalSearchScope.projectScope(myFixture.getProject()));
      }
      assertNotNull(aClass, "Class Test not found");

      final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
      final ParameterInfoImpl[] mergedParams = function.apply(method);
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
      StandardFileSystems.local().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  @Test
  public void testIntegerWrapper() {
    doTestExistingClass("Integer", "java.lang", false);
  }

  @Test
  public void testIntegerIncremental() {
    checkExceptionThrown("Integer", "java.lang", "Setter for field 'value' is required");
  }

  private void checkExceptionThrown(@NotNull String existingClassName, String existingClassPackage, String exceptionMessage) {
    try {
      doTestExistingClass(existingClassName, existingClassPackage, false);
      fail("Conflict was not found");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(exceptionMessage, e.getMessage());
    }
  }

  @Test
  public void testGenerateGetterSetterForExistingBean() {
    doTestExistingClass("Param", "", true);
  }

  @Test
  public void testExistingBeanVisibility() {
    doTestExistingClass("Param", "p", false, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  @Test
  public void testExistingBeanIfNoGeneration() {
    checkExceptionThrown("Param", "", "Setter for field 'i' is required");
  }

  @Test
  public void testParamNameConflict() {
    doTestExistingClass("Param", "", true);
  }

  @Test
  public void testExistingRecord() {
    doTestExistingClass("FullName", "", false);
  }

  @Test
  public void testExistingRecordWithCompactConstructor() {
    doTestExistingClass("FullName", "", false);
  }

  @Test
  public void testExistingClassDelegatingConstructor() {
    doTestExistingClass("Person", "", true);
  }

  @Test
  public void testExistentBean() {
    doTestExistingClass("Param", "", false);
  }

  @Test
  public void testExistingWithAnotherFieldNames() {
    doTestExistingClass("Param", "", true);
  }

  @Test
  public void testWrongBean() {
    checkExceptionThrown("Param", "", "Getter for field 'i' is required");
  }
}
