// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.method.MethodHierarchyTreeStructure;
import com.intellij.ide.hierarchy.type.TypeHierarchyNodeDescriptor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;

import java.util.Iterator;

public class JavaHierarchyNodeDescriptorTest extends LightJavaCodeInsightTestCase {
  public void testCallHierarchyStrikesThroughDeprecatedMethod() {
    PsiMethod method = getElementAtCaret(PsiMethod.class, """
      class A {
        @Deprecated
        void <caret>oldMethod() {}
      }
      """);

    assertMainSectionStruckOut(new CallHierarchyNodeDescriptor(getProject(), null, method, true, false));
  }

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

    HierarchyNodeDescriptor descriptor = new MethodHierarchyTreeStructure(
      getProject(),
      method,
      HierarchyBrowserBaseEx.SCOPE_PROJECT
    ).getBaseDescriptor();

    assertMainSectionStruckOut(descriptor);
  }

  public void testTypeHierarchyStrikesThroughDeprecatedClass() {
    PsiClass psiClass = getElementAtCaret(PsiClass.class, """
      @Deprecated
      class <caret>DeprecatedType {}
      """);

    assertMainSectionStruckOut(new TypeHierarchyNodeDescriptor(getProject(), null, psiClass, true));
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
    configureFromFileText("Test.java", text);
    return findElementAtCaret(elementClass);
  }

  private <T extends PsiElement> T findElementAtCaret(Class<T> elementClass) {
    T element = PsiTreeUtil.getParentOfType(findElementAtCaret(), elementClass);
    if (element == null) Assertions.fail("Element at caret does not have parent of type " + elementClass.getName());
    return element;
  }

  private PsiElement findElementAtCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement psiElement = getFile().findElementAt(offset);
    if (psiElement == null) Assertions.fail("Could not find PsiElement at caret position");
    return psiElement;
  }
}
