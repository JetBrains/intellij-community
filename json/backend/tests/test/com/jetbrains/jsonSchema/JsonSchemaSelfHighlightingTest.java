/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/selfHighlighting")
public class JsonSchemaSelfHighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture);

  private CodeInsightTestFixture myFixture;
  private JsonSchemaHeavyTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
  }

  private void doSelfTest(String schemaFile, @Language("JSON") String expectedText) {
    mySchemaFixture.skeleton(new JsonSchemaHeavyTestFixture.Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration pattern =
          new UserDefinedJsonSchemaConfiguration("pattern", JsonSchemaVersion.SCHEMA_4, mySchemaFixture.getUrlUnderTestRoot(schemaFile), false, Collections.emptyList());
        mySchemaFixture.addSchema(pattern);
        mySchemaFixture.setDoCompletion(false);
      }

      @Override
      public void configureFiles() {
        myFixture.configureByFiles(schemaFile);
      }

      @Override
      public void doCheck() {
        ExpectedHighlightingData data =
          new ExpectedHighlightingData(new DocumentImpl(expectedText), true, true, false);
        data.init();
        ((CodeInsightTestFixtureImpl)myFixture).collectAndCheckHighlighting(data);
      }
    });
  }

  @Test
  public void testPatterns() {
    myFixture.enableInspections(new JsonSchemaComplianceInspection());
    doSelfTest("/patternSchema.json", """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]$"
          },
          "everythingFine": {
            "pattern": "^[a]$"
          }
        },
        "patternProperties": {
          "p[0-9<error>"</error>: {},
          "b[0-7<error>"</error>: {}
        }
      }""");
  }

  @Test
  public void testRefs() {
    myFixture.enableInspections(new JsonSchemaRefReferenceInspection());
    doSelfTest("/refsSchema.json", """
      {
        "type": "object",
        "properties": {
          "test" : {
            "$ref": "refsSchema.json"
          },
          "test2": {
            "$ref": "refsSchema.json#/type"
          },
          "fail": {
            "$ref": "<warning>nonExisting.json</warning>"
          },
          "fail2": {
            "$ref": "refsSchema.json#/<warning>zzz</warning>"
          }
        }
      }""");
  }
}
