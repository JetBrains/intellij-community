// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.type.SubtypesHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.SupertypesHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;
import org.jetbrains.annotations.NotNull;

public class JavaTypeHierarchyTest extends HierarchyViewTestBase {
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
    return "ide/hierarchy/type/" + getTestName(false);
  }

  public void testClassTypeHierarchy() {
    doTypeHierarchyTest("C");
  }

  public void testClassSubtypesHierarchy() {
    doSubtypesHierarchyTest("B2");
  }

  public void testClassSupertypesHierarchy() {
    doSupertypesHierarchyTest("C");
  }

  public void testInterfaceTypeHierarchy() {
    doTypeHierarchyTest("C");
  }

  public void testInterfaceSubtypesHierarchy() {
    doSubtypesHierarchyTest("C");
  }

  public void testInterfaceSupertypesHierarchy() {
    doSupertypesHierarchyTest("C");
  }

  public void testNestedNamesHierarchy() {
    doTypeHierarchyTest("com.mypackage.Outer.Middle.Nested");
  }

  private void doTypeHierarchyTest(@NotNull String classFqn) {
    doHierarchyTest(() -> {
      PsiClass psiClass = findClass(classFqn);
      return new TypeHierarchyTreeStructure(getProject(), psiClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "X.java");
  }

  private void doSubtypesHierarchyTest(@NotNull String classFqn) {
    doHierarchyTest(() -> {
      PsiClass psiClass = findClass(classFqn);
      return new SubtypesHierarchyTreeStructure(getProject(), psiClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(myProject), "X.java");
  }

  private void doSupertypesHierarchyTest(@NotNull String classFqn) {
    doHierarchyTest(() -> {
      PsiClass psiClass = findClass(classFqn);
      return new SupertypesHierarchyTreeStructure(getProject(), psiClass);
    }, JavaHierarchyUtil.getComparator(myProject), "X.java");
  }
}
