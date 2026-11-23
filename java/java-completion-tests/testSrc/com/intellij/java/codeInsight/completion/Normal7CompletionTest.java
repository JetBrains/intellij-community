// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;

@TestApplication
@IndexingModeTemplate
public class Normal7CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testGenericInsideDiamond() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
}
