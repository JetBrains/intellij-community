// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class LimitedScopeInnerClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new LimitedScopeInnerClassInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
             class X {
               void x() {
                 class /*Local class 'Y'*/Y/**/ {}
               }
             }""");
  }

  @Test
  public void testEnum() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest("""
             class X {
                 void x() {
                     enum /*Local class 'E'*//*_*/E/**/ {
                        A, B
                     }
                 }
             }""");
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("move.local.to.inner.quickfix"));
    assertEquals("""
                   class X {
                       void x() {
                       }
                  
                       private enum E {
                          A, B
                       }
                   }""", myFixture.getIntentionPreviewText(intention));
  }
}