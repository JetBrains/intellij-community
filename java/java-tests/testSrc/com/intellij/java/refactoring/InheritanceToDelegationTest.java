// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationProcessor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class InheritanceToDelegationTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inheritanceToDelegation/";
  }

  public void testSimpleInsertion() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testSimpleGenerics() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testSuperCalls() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testGetter() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  public void testSubClass() {
    doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true,
                          true));
  }

  public void testSubClassNoMethods() {
    doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  public void testInterfaces() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, new String[]{"I"}, true, true));
  }

  public void testInnerClass() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testAbstractBase() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testAbstractBase1() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testHierarchy() {
    doTest(createPerformAction("X", "myDelegate", "MyBase", "Base", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testOverridenMethods() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testAnnotations() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testInnerClassForInterface() {
    doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
                               new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testInnerClassForInterfaceAbstract() {
    doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
                               new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testSubinterface() {
    doTest(createPerformAction("A", "myDelegate", "MyJ", "J", new int[0], ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  public void testInterfaceDelegation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{0}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19675
  public void testInterfaceImplicitImplementation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19699
  public void testMultipleInterfaceDelegation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf2", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  public void testScr20557() {
    doTest(createPerformAction2("xxx.SCR20557", "myResultSet", "MyResultSet", "java.sql.ResultSet",
                                new String[]{"getDate"}, ArrayUtilRt.EMPTY_STRING_ARRAY, false, false));
  }

  public void testTypeParametersSubstitution() {
     doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtilRt.EMPTY_STRING_ARRAY, true, false));
  }

  private ThrowableRunnable<Exception> createPerformAction(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final int[] methodIndices, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    return () -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull("Class " + className + " not found", aClass);
      PsiClass baseClass = myFixture.findClass(baseClassName);
      assertNotNull("Base class " + baseClassName + " not found", baseClass);
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
        getProject(),
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
    };
  }

  private ThrowableRunnable<Exception> createPerformAction2(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final String[] methodNames, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    return () -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull("Class " + className + " not found", aClass);
      PsiClass baseClass = myFixture.findClass(baseClassName);
      assertNotNull("Base class " + baseClassName + " not found", baseClass);
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
        getProject(),
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
      //FileDocumentManager.getInstance().saveAllDocuments();
    };
  }

}
