// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.method.MethodHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.TypeHierarchyNodeDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaHierarchyNodeDescriptorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testCallHierarchyStrikesThroughDeprecatedMethod() {
    PsiMethod method = getElementAtCaret(PsiMethod.class, """
      class A {
        @Deprecated
        void <caret>oldMethod() {}
      }
      """);

    Project project = daemonFixture.get().getFixture().getProject();
    ReadAction.runBlocking(() -> assertMainSectionStruckOut(new CallHierarchyNodeDescriptor(project, null, method, true, false)));
  }

  @Test
  public void testMethodHierarchyStrikesThroughDeprecatedClass() {
    PsiMethod method = getElementAtCaret(PsiMethod.class, """
      class Base {
        void foo() {}
      }

      @Deprecated
      class Derived extends Base {
        @Override
        void <caret>foo() {}
      }
      """);

    Project project = daemonFixture.get().getFixture().getProject();
    ReadAction.runBlocking(() -> {
      HierarchyNodeDescriptor descriptor = new MethodHierarchyTreeStructure(
        project,
        method,
        HierarchyBrowserBaseEx.SCOPE_PROJECT
      ).getBaseDescriptor();

      assertMainSectionStruckOut(descriptor);
    });
  }

  @Test
  public void testTypeHierarchyStrikesThroughDeprecatedClass() {
    PsiClass psiClass = getElementAtCaret(PsiClass.class, """
      @Deprecated
      class <caret>DeprecatedType {}
      """);

    Project project = daemonFixture.get().getFixture().getProject();
    ReadAction.runBlocking(() -> assertMainSectionStruckOut(new TypeHierarchyNodeDescriptor(project, null, psiClass, true)));
  }

  private static void assertMainSectionStruckOut(HierarchyNodeDescriptor descriptor) {
    descriptor.update();

    assertEquals(EffectType.STRIKEOUT, firstSection(descriptor.getHighlightedText()).getTextAttributes().getEffectType());
  }

  private static CompositeAppearance.TextSection firstSection(CompositeAppearance appearance) {
    Iterator<CompositeAppearance.TextSection> iterator = appearance.getSectionsIterator();
    assertTrue(iterator.hasNext());
    return iterator.next();
  }

  private <T extends PsiElement> T getElementAtCaret(Class<T> elementClass, @Language("JAVA") String text) {
    daemonFixture.get().configureFromFileText("Test.java", text);
    return ReadAction.computeBlocking(() -> findElementAtCaret(elementClass));
  }

  private <T extends PsiElement> T findElementAtCaret(Class<T> elementClass) {
    T element = PsiTreeUtil.getParentOfType(findElementAtCaret(), elementClass);
    if (element == null) Assertions.fail("Element at caret does not have parent of type " + elementClass.getName());
    return element;
  }

  private PsiElement findElementAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiElement psiElement = myFixture.getFile().findElementAt(offset);
    if (psiElement == null) Assertions.fail("Could not find PsiElement at caret position");
    return psiElement;
  }
}
