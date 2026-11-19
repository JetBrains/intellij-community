// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class InheritanceToDelegationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/inheritanceToDelegation/");

  @Test
  public void testSimpleInsertion() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testSimpleGenerics() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testSuperCalls() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testGetter() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  @Test
  public void testSubClass() {
    multiFile.get().doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true,
                          true));
  }

  @Test
  public void testSubClassNoMethods() {
    multiFile.get().doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  @Test
  public void testInterfaces() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, new String[]{"I"}, true, true));
  }

  @Test
  public void testInnerClass() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testAbstractBase() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testAbstractBase1() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testHierarchy() {
    multiFile.get().doTest(createPerformAction("X", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testOverridenMethods() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testAnnotations() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testInnerClassForInterface() {
    multiFile.get().doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
                               new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testInnerClassForInterfaceAbstract() {
    multiFile.get().doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
                               new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testSubinterface() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyJ", "J", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  @Test
  public void testInterfaceDelegation() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19675
  @Test
  public void testInterfaceImplicitImplementation() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19699
  @Test
  public void testMultipleInterfaceDelegation() {
    multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf2", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testScr20557() {
    multiFile.get().doTest(createPerformAction2("xxx.SCR20557", "myResultSet", "MyResultSet", "java.sql.ResultSet",
                                new String[]{"getDate"}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  @Test
  public void testTypeParametersSubstitution() {
     multiFile.get().doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  @Test
  public void testSealedParent() {
    multiFile.get().doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  private ThrowableRunnable<Exception> createPerformAction(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final int[] methodIndices, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    return () -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass, "Class " + className + " not found");
      PsiClass baseClass = myFixture.findClass(baseClassName);
      assertNotNull(baseClass, "Base class " + baseClassName + " not found");
      final PsiMethod[] methods = baseClass.getMethods();
      final PsiMethod[] delegatedMethods = new PsiMethod[methodIndices.length];
      for (int i = 0; i < methodIndices.length; i++) {
        delegatedMethods[i] = methods[methodIndices[i]];
      }
      final PsiClass[] delegatedInterfaces = new PsiClass[delegatedInterfaceNames.length];
      for (int i = 0; i < delegatedInterfaceNames.length; i++) {
        String delegatedInterfaceName = delegatedInterfaceNames[i];
        PsiClass anInterface = myFixture.findClass(delegatedInterfaceName);
        assertNotNull(anInterface);
        delegatedInterfaces[i] = anInterface;
      }
      new InheritanceToDelegationProcessor(
        myFixture.getProject(),
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
    };
  }

  private ThrowableRunnable<Exception> createPerformAction2(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final String[] methodNames, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    return () -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass, "Class " + className + " not found");
      PsiClass baseClass = myFixture.findClass(baseClassName);
      assertNotNull(baseClass, "Base class " + baseClassName + " not found");
      final PsiMethod[] delegatedMethods;
      final List<PsiMethod> methodsList = new ArrayList<>();
      for (String name : methodNames) {
        final PsiMethod[] methodsByName = baseClass.findMethodsByName(name, false);
        ContainerUtil.addAll(methodsList, methodsByName);
      }
      delegatedMethods = methodsList.toArray(PsiMethod.EMPTY_ARRAY);

      final PsiClass[] delegatedInterfaces = new PsiClass[delegatedInterfaceNames.length];
      for (int i = 0; i < delegatedInterfaceNames.length; i++) {
        String delegatedInterfaceName = delegatedInterfaceNames[i];
        PsiClass anInterface = myFixture.findClass(delegatedInterfaceName);
        assertNotNull(anInterface);
        delegatedInterfaces[i] = anInterface;
      }
      new InheritanceToDelegationProcessor(
        myFixture.getProject(),
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
      //FileDocumentManager.getInstance().saveAllDocuments();
    };
  }

}
