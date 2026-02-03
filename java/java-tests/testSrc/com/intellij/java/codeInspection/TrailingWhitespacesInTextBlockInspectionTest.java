// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.TrailingWhitespacesInTextBlockInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TrailingWhitespacesInTextBlockInspectionTest extends LightJavaInspectionTestCase {

  public void testBadTextBlock() { doTest(); }
  public void testBlankLine() { doTestAll(); }
  public void testEmptyLine() { doTestEscape(); }
  public void testEmptyLastLine() { doTestAll(); }
  public void testOneLineBlock() { doTestAll(); }
  public void testReplaceAtTheEnd() { doTestEscape(); }
  public void testStringTemplate1Java21Preview() { doTestEscape(); }
  public void testStringTemplate2Java21Preview() { doTestEscape(); }
  public void testStringTemplate3Java21Preview() { doTestEscape(); }
  public void testStringTemplate4Java21Preview() { doTestEscape(); }
  public void testStringTemplate5() { doTest(); }
  public void testStringTemplate6() { doTestAll(); }
  public void testStringTemplateOneLine() { doTestAll(); }
  public void testTrailingSpaces() { doTestAll(); }
  public void testTrailingTabs() { doTestEscape(); }
  public void testWithBlankLines() { doTestAll(); }
  public void testWithEndQuotes() { doTestAll(); }
  public void testWithoutTrailingSpaces() { doTest(); }
  public void testWithUnicodeEscapes() { doTestEscape(); }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new TrailingWhitespacesInTextBlockInspection();
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/inspection/trailingWhitespacesInTextBlock";
  }

  private void doTestEscape() {
    doTest();
    checkQuickFix("Escape trailing whitespace characters");
  }

  private void doTestAll() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }
}
