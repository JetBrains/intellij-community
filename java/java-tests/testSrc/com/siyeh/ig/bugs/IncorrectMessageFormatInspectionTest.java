// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IncorrectMessageFormatInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/bugs/incorrect_message_format";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  private void doTest(List<String> classes, List<String> methods) {
    IncorrectMessageFormatInspection inspection = new IncorrectMessageFormatInspection();
    MethodMatcher matcher = inspection.myMethodMatcher;
    for (int i = 0; i < classes.size(); i++) {
      matcher.add(classes.get(i), methods.get(i));
    }
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIncorrectMessageFormat() {
    doTest(List.of(), List.of());
  }

  public void testIncorrectMessageFormatWithCustomMethods() {
    doTest(List.of("org.example.util.Formatter"), List.of("formatNew"));
  }
}
