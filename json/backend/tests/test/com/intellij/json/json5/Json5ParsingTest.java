// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.JsonParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
@TestDataPath("$CONTENT_ROOT/testData/psi/")
public class Json5ParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/psi", "json5",
                   new Json5ParserDefinition(), new JsonParserDefinition());

  private void doTest() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testJson5Syntax() {
    doTest();
  }
}
