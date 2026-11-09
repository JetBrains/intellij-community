// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class WhileLoopSpinsOnFieldInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testWhileLoopSpinsOnField() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, inspectionFixture.get()::doTest);
  }

  @Test
  public void testConditionBoundedBuffer() {
    // Sample from JCIP
    // See IDEA-364908
    inspectionFixture.get().doTest();
  }

  @Test
  public void testMultiFileFix() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addFileToProject("F1.java", """
      public class F1 {
          boolean f;
      }
      """);
    inspectionFixture.get().doTest();
    IntentionAction action = myFixture.findSingleIntention("Make 'f' volatile and add Thread.onSpinWait()");
    var text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
      class F2 extends F1 {
          void test() {
              while (f) {
                  Thread.onSpinWait();
                         
              }
          }
      }
                         
      ----------
      public class F1 {
          volatile boolean f;
      }
      """, text);
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    WhileLoopSpinsOnFieldInspection inspection = new WhileLoopSpinsOnFieldInspection();
    inspection.ignoreNonEmtpyLoops = false;
    return inspection;
  }
}