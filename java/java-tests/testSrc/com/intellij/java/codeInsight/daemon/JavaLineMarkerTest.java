// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public final class JavaLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testOverrideFunctionalInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testOverrideFunctionalInterfaceUnused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Gutter icons are absent if the interface is unused completely
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertTrue(gutters.isEmpty());
  }

  @Test
  public void testOverrideNormalInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Gutter icons on functional interfaces are always present, as function search can be slow
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
        void doAnotherThing();
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertTrue(gutters.isEmpty());
  }

  @Test
  public void testImplementedRecordMethodsInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
      myFixture.getProject(),
      () -> {
        ReadAction.runBlocking(() -> {
          PsiElement psiElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
          LineMarkerInfo<?> info = new JavaLineMarkerProvider().getLineMarkerInfo(psiElement);
          assertNotNull(info);
          assertEquals(AllIcons.Gutter.ImplementingMethod, info.getIcon());
        });
      });
  }
}
