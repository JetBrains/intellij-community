// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class CStyleArrayDeclarationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/style/cstyle_array_declaration";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16;
  }

  private void doTest() {
    myFixture.enableInspections(new CStyleArrayDeclarationInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
    String inspectionName = InspectionGadgetsBundle.message("c.style.array.declaration.display.name");
    IntentionAction intention =
      myFixture.findSingleIntention(InspectionsBundle.message("fix.all.inspection.problems.in.file", inspectionName));
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  public void testCStyleArrayDeclaration() {
    doTest();
  }

}
