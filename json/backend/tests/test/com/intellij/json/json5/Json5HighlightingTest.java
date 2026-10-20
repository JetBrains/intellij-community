// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonHighlightingTestFixture;
import com.intellij.json.json5.codeinsight.Json5StandardComplianceInspection;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.json.JsonHighlightingTestFixtureKt.jsonHighlightingFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class Json5HighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonHighlightingTestFixture> highlightingFixture = jsonHighlightingFixture(codeInsightFixture, "json5");

  @Test
  public void testJSON5() {
    highlightingFixture.get().getFixture().enableInspections(new Json5StandardComplianceInspection());
    highlightingFixture.get().doTestHighlighting(false, true, true);
  }
}
