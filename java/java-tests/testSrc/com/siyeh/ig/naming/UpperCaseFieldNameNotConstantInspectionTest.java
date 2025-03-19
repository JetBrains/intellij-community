// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.naming;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UpperCaseFieldNameNotConstantInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/naming/upper_case_field_name_not_constant";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new UpperCaseFieldNameNotConstantInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUpperCaseFieldNameNotConstant() {
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention(InspectionGadgetsBundle.message("rename.quickfix"));
    assertNotNull(intention);
    myFixture.checkIntentionPreviewHtml(intention, "Renames field &#39;FOO&#39; and its usages");
  }
}
