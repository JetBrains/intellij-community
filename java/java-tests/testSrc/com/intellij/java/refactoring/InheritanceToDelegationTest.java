/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class InheritanceToDelegationTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/inheritanceToDelegation/";
  }

  public void testSimpleInsertion() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testSimpleGenerics() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testSuperCalls() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testGetter() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0, 1}, ArrayUtil.EMPTY_STRING_ARRAY, true, true));
  }

  public void testSubClass() {
    doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, true,
          true));
  }

  public void testSubClassNoMethods() {
    doTest(
      createPerformAction("A", "myDelegate", "MyDelegatedBase", "DelegatedBase", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, true, true));
  }

  public void testInterfaces() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, new String[]{"I"}, true, true));
  }

  public void testInnerClass() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testAbstractBase() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testAbstractBase1() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testHierarchy() {
    doTest(createPerformAction("X", "myDelegate", "MyBase", "Base", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testOverridenMethods() {
    doTest(createPerformAction("A", "myDelegate", "MyBase", "Base", new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testAnnotations() {
    doTest(createPerformAction("B", "myDelegate", "MyA", "A", new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testInnerClassForInterface() {
    doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
        new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testInnerClassForInterfaceAbstract() {
    doTest(createPerformAction("A", "myBaseInterface", "MyBaseInterface", "BaseInterface",
        new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testSubinterface() {
    doTest(createPerformAction("A", "myDelegate", "MyJ", "J", new int[0], ArrayUtil.EMPTY_STRING_ARRAY, true, true));
  }

  public void testInterfaceDelegation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{0}, ArrayUtil.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19675
  public void testInterfaceImplicitImplementation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtil.EMPTY_STRING_ARRAY, true, true));
  }

  // IDEADEV-19699
  public void testMultipleInterfaceDelegation() {
    doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf2", new int[]{}, ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  public void testScr20557() {
    doTest(createPerformAction2("xxx.SCR20557", "myResultSet", "MyResultSet", "java.sql.ResultSet",
        new String[]{"getDate"}, ArrayUtil.EMPTY_STRING_ARRAY, false, false));
  }

  public void testTypeParametersSubstitution() {
     doTest(createPerformAction("A", "myDelegate", "MyIntf", "Intf", new int[]{}, ArrayUtil.EMPTY_STRING_ARRAY, true, false));
  }

  private PerformAction createPerformAction(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final int[] methodIndices, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    return (rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", aClass);
      PsiClass baseClass = myJavaFacade.findClass(baseClassName, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Base class " + baseClassName + " not found", baseClass);
      final PsiMethod[] methods = baseClass.getMethods();
      final PsiMethod[] delegatedMethods = new PsiMethod[methodIndices.length];
      for (int i = 0; i < methodIndices.length; i++) {
        delegatedMethods[i] = methods[methodIndices[i]];
      }
      final PsiClass[] delegatedInterfaces = new PsiClass[delegatedInterfaceNames.length];
      for (int i = 0; i < delegatedInterfaceNames.length; i++) {
        String delegatedInterfaceName = delegatedInterfaceNames[i];
        PsiClass anInterface = myJavaFacade.findClass(delegatedInterfaceName, GlobalSearchScope.allScope(getProject()));
        assertNotNull(anInterface);
        delegatedInterfaces[i] = anInterface;
      }
      new InheritanceToDelegationProcessor(
        myProject,
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
    };
  }

  private PerformAction createPerformAction2(
    final String className, final String fieldName, final String innerClassName,
    final String baseClassName, final String[] methodNames, final String[] delegatedInterfaceNames,
    final boolean delegateOtherMembers, final boolean generateGetter) {
    return (rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", aClass);
      PsiClass baseClass = myJavaFacade.findClass(baseClassName, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Base class " + baseClassName + " not found", baseClass);
      final PsiMethod[] delegatedMethods;
      final List<PsiMethod> methodsList = new ArrayList<>();
      for (String name : methodNames) {
        final PsiMethod[] methodsByName = baseClass.findMethodsByName(name, false);
        ContainerUtil.addAll(methodsList, methodsByName);
      }
      delegatedMethods = methodsList.toArray(new PsiMethod[methodsList.size()]);

      final PsiClass[] delegatedInterfaces = new PsiClass[delegatedInterfaceNames.length];
      for (int i = 0; i < delegatedInterfaceNames.length; i++) {
        String delegatedInterfaceName = delegatedInterfaceNames[i];
        PsiClass anInterface = myJavaFacade.findClass(delegatedInterfaceName, GlobalSearchScope.allScope(getProject()));
        assertNotNull(anInterface);
        delegatedInterfaces[i] = anInterface;
      }
      new InheritanceToDelegationProcessor(
        myProject,
        aClass, baseClass, fieldName, innerClassName, delegatedInterfaces, delegatedMethods, delegateOtherMembers,
        generateGetter).run();
      //FileDocumentManager.getInstance().saveAllDocuments();
    };
  }

}
