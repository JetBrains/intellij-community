// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.JavaTestUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.type.SubtypesHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.SupertypesHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewFixture.hierarchyViewFixture;

@TestApplication
public class JavaTypeHierarchyTest {
  private final TestFixture<HierarchyViewFixture> hierarchyFixture =
    hierarchyViewFixture(JavaTestUtil.getJavaTestDataPath(), "ide/hierarchy/type", () -> IdeaTestUtil.getMockJdk18());

  @Test
  public void testClassTypeHierarchy() {
    doTypeHierarchyTest("C");
  }

  @Test
  public void testClassSubtypesHierarchy() {
    doSubtypesHierarchyTest("B2");
  }

  @Test
  public void testClassSupertypesHierarchy() {
    doSupertypesHierarchyTest("C");
  }

  @Test
  public void testInterfaceTypeHierarchy() {
    doTypeHierarchyTest("C");
  }

  @Test
  public void testInterfaceSubtypesHierarchy() {
    doSubtypesHierarchyTest("C");
  }

  @Test
  public void testInterfaceSupertypesHierarchy() {
    doSupertypesHierarchyTest("C");
  }

  @Test
  public void testNestedNamesHierarchy() {
    doTypeHierarchyTest("com.mypackage.Outer.Middle.Nested");
  }

  private void doTypeHierarchyTest(@NotNull String classFqn) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = fixture.findClass(classFqn);
      return new TypeHierarchyTreeStructure(fixture.getProject(), psiClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(fixture.getProject()), "X.java");
  }

  private void doSubtypesHierarchyTest(@NotNull String classFqn) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = fixture.findClass(classFqn);
      return new SubtypesHierarchyTreeStructure(fixture.getProject(), psiClass, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }, JavaHierarchyUtil.getComparator(fixture.getProject()), "X.java");
  }

  private void doSupertypesHierarchyTest(@NotNull String classFqn) {
    HierarchyViewFixture fixture = hierarchyFixture.get();
    fixture.doHierarchyTest(() -> {
      PsiClass psiClass = fixture.findClass(classFqn);
      return new SupertypesHierarchyTreeStructure(fixture.getProject(), psiClass);
    }, JavaHierarchyUtil.getComparator(fixture.getProject()), "X.java");
  }
}
