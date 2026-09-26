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

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaRefReferenceInspection;
import org.intellij.lang.annotations.Language;

import java.util.Collections;

public class JsonSchemaSelfHighlightingTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/selfHighlighting";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  private void doSelfTest(String schemaFile, @Language("JSON") String expectedText) throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final UserDefinedJsonSchemaConfiguration pattern =
          new UserDefinedJsonSchemaConfiguration("pattern", JsonSchemaVersion.SCHEMA_4, getUrlUnderTestRoot(schemaFile), false, Collections.emptyList());
        addSchema(pattern);
        myDoCompletion = false;
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

  public void testPatterns() throws Exception {
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

  public void testRefs() throws Exception {
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
