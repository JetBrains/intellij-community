// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class UncheckedWarningInferenceTest extends LightJavaCodeInsightFixtureTestCase5 {

  @NotNull
  @Override
  protected String getRelativePath() {
    return super.getRelativePath() + "/codeInsight/daemonCodeAnalyzer/lambda/unchecked/";
  }

  @Test
  void testDontTreatAsUncheckedIfRawWasAssignedInRawParameter() { doTest(); }

  private void doTest() {
    getFixture().testHighlighting(getTestName(false) + ".java");
  }
}