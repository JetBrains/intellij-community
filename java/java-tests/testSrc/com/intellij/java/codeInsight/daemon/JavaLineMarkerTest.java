// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public final class JavaLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testOverrideFunctionalInterface() {
    // Gutter icons on used functional interfaces are always present, as function search can be slow
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
      
        static void m() {
          Test test;
        }
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(2, gutters.size());
    assertEquals(AllIcons.Gutter.ImplementedMethod, gutters.get(0).getIcon());
    assertEquals(AllIcons.Gutter.ImplementedMethod, gutters.get(1).getIcon());
  }

  public void testOverrideFunctionalInterfaceUnused() {
    // Gutter icons are absent if the interface is unused completely
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertTrue(gutters.isEmpty());
  }

  public void testOverrideNormalInterface() {
    // Gutter icons on functional interfaces are always present, as function search can be slow
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
        void doAnotherThing();
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertTrue(gutters.isEmpty());
  }

  public void testImplementedRecordMethodsInDumbMode() {
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
      }""");
    myFixture.configureByText("RecordTest.java", """
      record RecordTest() implements Test {
        @Override
        public void do<caret>Something();
      }""");
    DumbModeTestUtils.runInDumbModeSynchronously(
      getProject(),
      () -> {
        PsiElement psiElement = getFile().findElementAt(getEditor().getCaretModel().getOffset());
        LineMarkerInfo<?> info = new JavaLineMarkerProvider().getLineMarkerInfo(psiElement);
        assertNotNull(info);
        assertEquals(AllIcons.Gutter.ImplementingMethod, info.getIcon());
      });
  }
}
