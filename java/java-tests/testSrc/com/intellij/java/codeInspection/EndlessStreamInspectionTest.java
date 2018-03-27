// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.EndlessStreamInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class EndlessStreamInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testCollect() {doTest();}
  public void testSorted() {doTest();}
  public void testLimited() {doTest();}

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/endlessStream";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  private void doTest() {
    myFixture.enableInspections(new EndlessStreamInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}