// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/documentation")
public class JsonSchemaDocumentationTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture);

  private JsonSchemaHeavyTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    mySchemaFixture = schemaFixture.get();
  }

  @Test
  public void testSimple() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testSecondLevel() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testCheckEscaping() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testWithDefinition() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testWithTitleInDefinition() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testMarkdownDescription() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testHtmlDescription() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }

  @Test
  public void testAdditionalPropertyDocs() {
    mySchemaFixture.doDocumentationTest(true, "json");
  }
}
