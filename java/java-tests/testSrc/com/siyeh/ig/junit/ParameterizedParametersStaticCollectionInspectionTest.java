// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ParameterizedParametersStaticCollectionInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/junit/parameterized";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
                         package org.junit.runner;
                         public @interface RunWith {
                             Class value();
                         }
                         """);
    myFixture.addClass("""
                         package org.junit.runners;
                         public class Parameterized {    public @interface Parameters {
                                 String name() default "{index}";
                             }}\s""");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new ParameterizedParametersStaticCollectionInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  private void checkQuickFix(@NotNull @Nls String intentionName) {
    final IntentionAction intention = myFixture.getAvailableIntention(intentionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  public void testCreatemethod() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("fix.data.provider.create.method.fix.name"));
  }

  public void testWrongsignature() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("fix.data.provider.signature.fix.name", "public static Collection regExValues()"));
  }

  public void testWrongsignature1() { doTest(); }
  public void testWrongsignature2() { doTest(); }
  public void testWrongsignature3() { doTest(); }
  public void testCorrectSignature() { doTest(); }
  public void testCorrectSignature2() { doTest(); }
  public void testMultipleMethods() { doTest(); }

}