// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class TrivialStringConcatenationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/performance/trivial_string_concatenation";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest(boolean skipIfNecessary) {
    TrivialStringConcatenationInspection inspection = new TrivialStringConcatenationInspection();
    inspection.skipIfNecessary = skipIfNecessary;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testTrivialStringConcatenation() {
    doTest(false);
  }

  public void testTrivialStringConcatenationWithSkipNecessary() {
    doTest(true);
  }
}
