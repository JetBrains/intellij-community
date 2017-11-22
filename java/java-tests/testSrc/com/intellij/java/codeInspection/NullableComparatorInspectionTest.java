// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.NullableComparatorInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class NullableComparatorInspectionTest extends LightCodeInsightFixtureTestCase {

  private void doTest() {
    myFixture.enableInspections(new NullableComparatorInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testComparableList() { doTest(); }
  public void testNonComparableList() { doTest(); }
  public void testComparableStream() { doTest(); }
  public void testNonComparableStream() { doTest(); }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/nullableComparator/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
