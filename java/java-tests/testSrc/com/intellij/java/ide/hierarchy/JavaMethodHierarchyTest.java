// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.method.MethodHierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture.hierarchyViewFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaMethodHierarchyTest {
  private final TestFixture<HierarchyViewFixture> hierarchyFixture =
    hierarchyViewFixture(JavaTestUtil.getJavaTestDataPath(), "ide/hierarchy/method", () -> IdeaTestUtil.getMockJdk18(),
                         LanguageLevel.JDK_1_8); // default methods are needed

  @Test
  public void testNoHierarchy() {
    doTest("A", "foo", "A.java");
  }

  @Test
  public void testOnlyUp() {
    doTest("Z", "m", "X.java");
  }

  @Test
  public void testOnlyDown() {
    doTest("X", "m", "X.java");
  }

  @Test
  public void testOnlyDownHide() {
    doTestHideIrrelevantClasses("foo.X", "m", "X.java");
  }

  @Test
  public void testUpAndDown() {
    doTestHideIrrelevantClasses("Y", "foo", "X.java");
  }

  @Test
  public void testObjectMethod() {
    doTest("Foo", "hashCode", "X.java");
  }

  @Test
  public void testInterfaceInheritance() {
    doTest("E", "bar", "X.java");
  }

  @Test
  public void testCyclicInheritance() {
    doTest("D", "foo", "X.java");
  }

  @Test
  public void testExtendsImplementsChain() {
    doTest("C", "foo", "X.java");
  }

  @Test
  public void testTwoParentsPreferClass() {
    doTest("C3", "m", "X.java");
  }

  private void doTest(final String classFqn, final String methodName, final String... fileNames) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    Project project = fixture.getProject();
    fixture.doHierarchyTest(() -> {
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(classFqn, ProjectScope.getProjectScope(project));
      final PsiMethod method = psiClass.findMethodsByName(methodName, false) [0];
      return new MethodHierarchyTreeStructure(project, method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(project), fileNames);
  }

  private void doTestHideIrrelevantClasses(String classFqn, String methodName, String... fileNames) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(hierarchyFixture.get().getProject()).getState();
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
