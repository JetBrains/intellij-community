// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.longLine.LongLineInspection;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;

/**
 * @author Dmitry Batkovich
 */
public class LongLineInspectionTest extends BasePlatformTestCase {

  public void testShortLine() {
    doTest("java");
  }

  public void testLongLine() {
    doTest("java");
  }

  public void testLongLineWithTabs() {
    doTest("java");
  }

  public void testXmlLongLine() {
    doTest("xml");
  }

  public void testPlain() {
    doTest("txt");
  }

  private void doTest(final String extension) {
    myFixture.enableInspections(new LongLineInspection());
    myFixture.testHighlighting(true, false, false, getTestName(true) + "." + extension);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/lang-impl/testData/codeInspection/longLine/";
  }
}
