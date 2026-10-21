// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JsonSchemaInjectionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.json", () -> new JsonSchemaComplianceInspection(), JsonLanguage.INSTANCE);

  private JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    mySchemaFixture = schemaFixture.get();
  }

  @SuppressWarnings("SameParameterValue")
  private void doTest(@Language("JSON") String schema, @Language("JSON") String text, boolean shouldHaveInjection) {
    final PsiFile file = mySchemaFixture.configureInitially(schema, text, "json");
    checkInjection(shouldHaveInjection, file, JsonFile.class);
  }

  public static void checkInjection(boolean shouldHaveInjection, PsiFile file, Class<? extends PsiFile> ownLanguageFileClass) {
    assertNotNull(file);
    if (shouldHaveInjection) {
      assertTrue(file.getClass().getSimpleName().startsWith("XmlFile"));
    }
    else {
      assertInstanceOf(ownLanguageFileClass, file);
    }
  }

  @Test
  public void testXml() {
    doTest("""
             {
               "properties": {
                 "X": {
                   "x-intellij-language-injection": "XML"
                 }
               }
             }""", """
             {
               "X": "<a<caret>></a>"
             }""", true);
  }

  @Test
  public void testNoInjection() {
    doTest("""
             {
               "properties": {
                 "X": {
                 }
               }
             }""", """
             {
               "X": "<a<caret>></a>"
             }""", false);
  }

  @Test
  public void testOneOfSchema() {
    @Language("JSON") String schema = """
      {
        "additionalProperties": {
          "oneOf": [
            {
              "type": "string",
              "x-intellij-language-injection": "XML"
            },
            {
              "type": "array",
              "items": {
                "type": "string",
                "x-intellij-language-injection": "XML"
              }
            }
          ]
        }
      }""";
    doTest(schema, """
      {
        "foo": "<caret><a/>",
        "bar": ["<a/>"]
      }""", true);
    doTest(schema, """
      {
        "foo": "<a/>",
        "bar": ["<caret><a/>"]
      }""", true);
  }

  @Test
  public void testPrefixSuffix() {
    doTest("""
             {
               "properties": {
                 "x": {
                   "x-intellij-language-injection": {
                     "language": "XML",
                     "prefix": "<",
                     "suffix": ">"
                   }
                 }
               }
             }""", "{\"x\": \"x><caret></\"}", true);
  }
}
