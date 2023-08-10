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

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getBasePath() {
    return "ide/hierarchy/method/" + getTestName(false);
  }

  public void testNoHierarchy() throws Exception {
    doTest("A", "foo", "A.java");
  }

  public void testOnlyUp() throws Exception {
    doTest("Z", "m", "X.java");
  }

  public void testOnlyDown() throws Exception {
    doTest("X", "m", "X.java");
  }

  public void testOnlyDownHide() throws Exception {
    doTestHideIrrelevantClasses("foo.X", "m", "X.java");
  }

  public void testUpAndDown() throws Exception {
    doTestHideIrrelevantClasses("Y", "foo", "X.java");
  }

  public void testObjectMethod() throws Exception {
    doTest("Foo", "hashCode", "X.java");
  }

  public void testInterfaceInheritance() throws Exception {
    doTest("E", "bar", "X.java");
  }

  public void testCyclicInheritance() throws Exception {
    doTest("D", "foo", "X.java");
  }

  public void testExtendsImplementsChain() throws Exception {
    doTest("C", "foo", "X.java");
  }

  public void testTwoParentsPreferClass() throws Exception {
    doTest("C3", "m", "X.java");
  }

  private void doTest(final String classFqn, final String methodName, final String... fileNames) throws Exception {
    doHierarchyTest(() -> {
      final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(classFqn, ProjectScope.getProjectScope(getProject()));
      final PsiMethod method = psiClass.findMethodsByName(methodName, false) [0];
      return new MethodHierarchyTreeStructure(getProject(), method, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), fileNames);
  }

  private void doTestHideIrrelevantClasses(String classFqn, String methodName, String... fileNames) throws Exception {
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

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}
