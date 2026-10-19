// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.json5;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.impl.JsonSchemaCompletionTestUtil.testBySchema;

@TestApplication
public class Json5ByJsonSchemaCompletionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
  }

  @Test
  public void testTopLevel() throws Exception {
    testBySchema(myFixture, "{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{pri<caret>}", "someFile.json5", "prima", "primus", "proto");
  }

  @Test
  public void testAlreadyInsertedProperty() throws Exception {
    testBySchema(myFixture, "{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{prima: 1, pri<caret>}", "someFile.json5", "primus", "proto");
  }
}
