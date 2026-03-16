// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.method.MethodHierarchyTreeStructure;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;
import org.jetbrains.annotations.NotNull;

public class JavaMethodHierarchyTest extends HierarchyViewTestBase {
  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_1_8; // default methods are needed
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getBasePath() {
    return "ide/hierarchy/method/" + getTestName(false);
  }

  public void testNoHierarchy() {
    doTest("A", "foo", "A.java");
  }

  public void testOnlyUp() {
    doTest("Z", "m", "X.java");
  }

  public void testOnlyDown() {
    doTest("X", "m", "X.java");
  }

  public void testOnlyDownHide() {
    doTestHideIrrelevantClasses("foo.X", "m", "X.java");
  }

  public void testUpAndDown() {
    doTestHideIrrelevantClasses("Y", "foo", "X.java");
  }

  public void testObjectMethod() {
    doTest("Foo", "hashCode", "X.java");
  }

  public void testInterfaceInheritance() {
    doTest("E", "bar", "X.java");
  }

  public void testCyclicInheritance() {
    doTest("D", "foo", "X.java");
  }

  public void testExtendsImplementsChain() {
    doTest("C", "foo", "X.java");
  }

  public void testTwoParentsPreferClass() {
    doTest("C3", "m", "X.java");
  }

  private void doTest(final String classFqn, final String methodName, final String... fileNames) {
    doHierarchyTest(() -> {
      final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
      final PsiMethod method = psiClass.findMethodsByName(methodName, false) [0];
      return new MethodHierarchyTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), fileNames);
  }

  private void doTestHideIrrelevantClasses(String classFqn, String methodName, String... fileNames) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myProject).getState();
    assertNotNull(state);
    state.HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED = true;
    try {
      doTest(classFqn, methodName, fileNames);
    }
    finally {
      state.HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED = false;
    }
  }
}
