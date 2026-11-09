// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ProtectedInnerClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final ProtectedInnerClassInspection tool = new ProtectedInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    return tool;
  }

  @Test
  public void testProtectedInnerClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testPreviewStaticClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("Outer.java", "public class Outer { protected static class N<caret>ested {} }");
    IntentionAction action = myFixture.findSingleIntention("Move class");
    myFixture.checkIntentionPreviewHtml(action, "Move inner class 'Nested' to the top level of a package of your choice or to an another class.");
  }

  @Test
  public void testPreviewNonStaticClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("Outer.java", "public class Outer { protected class In<caret>ner {} }");
    IntentionAction action = myFixture.findSingleIntention("Move class");
    myFixture.checkIntentionPreviewHtml(action, "Move inner class 'Inner' to the top level of a package of your choice.");
  }
}
