// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class IncorrectDateTimeFormatInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/bugs/incorrect_date_time_format";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    //mock
    //noinspection InstantiationOfUtilityClass
    myFixture.addClass("package java.time.format;public class DateTimeFormatter{" +
                       "public static DateTimeFormatter ofPattern(String pattern) {return new DateTimeFormatter();}}");
    myFixture.enableInspections(new IncorrectDateTimeFormatInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIncorrectDateTimeFormat() {
    doTest();
  }
}
