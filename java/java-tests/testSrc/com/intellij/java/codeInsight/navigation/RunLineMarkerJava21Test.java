// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class RunLineMarkerJava21Test extends LightJavaCodeInsightFixtureTestCase {

  public void testUnnamedAllowsNonStatic() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  public void testClassWithConstructorWithoutParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        A() {}
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  public void testClassWithDefaultConstructorParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  public void testClassWithConstructorWithParametersAndInstanceMethodIsNotEntryPoint() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        A(String s) {}
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  public void testMainInsideInnerClassInUnnamedClassHasNoGutter() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void foo() {
      }
      
      public class A {
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  public void testStaticWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main<caret>() {}
      static void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testStaticWithNoParametersHasHigherPriorityThanInstance() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main() {}
      void main<caret>(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testInstanceWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {}
      void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }
}
