// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public final class JavaLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testOverrideFunctionalInterface() {
    // Gutter icons on functional interfaces are always present, as function search can be slow
    myFixture.configureByText("Test.java", """
      interface Test {
        void doSomething();
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(2, gutters.size());
    assertEquals(AllIcons.Gutter.ImplementedMethod, gutters.get(0).getIcon());
    assertEquals(AllIcons.Gutter.ImplementedMethod, gutters.get(1).getIcon());
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
}
