// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixtureKt.jsonSchemaHeavyFixture;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData/jsonSchema/completion")
public class JsonBySchemaHeavyCompletionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHeavyTestFixture> schemaFixture = jsonSchemaHeavyFixture(codeInsightFixture, getExtensionWithoutDot());

  protected CodeInsightTestFixture myFixture;
  protected JsonSchemaHeavyTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
  }

  /**
   * The field initializer calls it, so an override must not read the state of the subclass.
   */
  protected String getExtensionWithoutDot() {
    return "json";
  }

  @Test
  public void testInsertEnumValue() {
    mySchemaFixture.baseInsertTest(mySchemaFixture.getTestName(true), "testValue");
  }

  @Test
  public void testInsertPropertyName() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testName");
  }

  @Test
  public void testInsertNameWithDefaultStringValue() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testNameWithDefaultStringValue");
  }

  @Test
  public void testIncompleteNameWithDefaultStringValue() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testIncompleteNameWithDefaultStringValue");
  }

  @Test
  public void testInsertNameWithDefaultIntegerValue() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testNameWithDefaultIntegerValue");
  }

  @Test
  public void testInsertIntegerType() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testIntegerType");
  }

  @Test
  public void testInsertStringType() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testStringType");
  }

  @Test
  public void testInsertObjectType() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testObjectType");
  }

  @Test
  public void testInsertArrayType() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testArrayType");
  }

  @Test
  public void testInsertBooleanType() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testBooleanType");
  }

  //no quotes
  @Test
  public void testNameWithDefaultStringValueNoQuotes() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testNameWithDefaultStringValueNoQuotes");
  }

  @Test
  public void testNameWithDefaultIntegerValueNoQuotesComma() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testNameWithDefaultIntegerValueNoQuotesComma");
  }

  //comma
  @Test
  public void testInsertIntegerTypeComma() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testIntegerTypeComma");
  }

  @Test
  public void testInsertBooleanTypeComma() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testBooleanTypeComma");
  }

  @Test
  public void testStringTypeComma() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testStringTypeComma");
  }

  @Test
  public void testNameWithDefaultStringValueComma() {
    mySchemaFixture.baseInsertTest("insertPropertyName", "testNameWithDefaultStringValueComma");
  }

  @Test
  public void testWhitespaceAfterColon() {
    mySchemaFixture.baseInsertTest("addWhitespaceAfterColon", "colon");
  }

  @Test
  public void testArrayLiteral() {
    mySchemaFixture.baseInsertTest("insertArrayOrObjectLiteral", "arrayLiteral");
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("1", "2", "3");
  }

  @Test
  public void testObjectLiteral() {
    mySchemaFixture.baseInsertTest("insertArrayOrObjectLiteral", "objectLiteral");
    mySchemaFixture.complete();
    mySchemaFixture.assertStringItems("\"insideTopObject1\"", "\"insideTopObject2\"");
  }

  @Test
  public void testOneOfWithNotFilledPropertyValue() {
    mySchemaFixture.baseCompletionTest("oneOfWithEnumValue", "oneOfWithEmptyPropertyValue", "\"business\"", "\"home\"");
  }

  @Test
  public void testRequiredPropsFirst() {
    mySchemaFixture.baseTestNoSchema("requiredProps", "requiredPropsFirst", () -> {
      mySchemaFixture.complete();
      mySchemaFixture.assertStringItems("a", "b");
    });
  }

  @Test
  public void testRequiredPropsLast() {
    mySchemaFixture.baseTestNoSchema("requiredProps", "requiredPropsLast", () -> {
      mySchemaFixture.complete();
      mySchemaFixture.assertStringItems("b");
    });
  }

  @Test
  public void testEditingSchemaAffectsCompletion() {
    mySchemaFixture.baseTest(mySchemaFixture.getTestName(true), "testEditing", () -> {
      mySchemaFixture.complete();
      mySchemaFixture.assertStringItems("\"preserve\"", "\"react\"", "\"react-native\"");

      final PsiFile schema = ReadAction.computeBlocking(() -> myFixture.getFile().getParent().findFile("Schema.json"));
      EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().runUndoTransparentAction(
        () -> WriteAction.run(
          () -> {
            try {
              schema.getVirtualFile().setBinaryContent(
                """
                  {
                    "properties": {
                      "jsx": {
                        "oneOf": [
                          {"enum": [ "preserve", "react" ]},
                          {"enum": [ "completelyChanged" ]}
                        ]
                      }
                    }
                  }
                  """.getBytes(StandardCharsets.UTF_8));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        )
      ));
      VfsUtil.markDirtyAndRefresh(false, false, true, schema.getVirtualFile());
      schema.getVirtualFile().refresh(false, false);
      mySchemaFixture.complete();
      mySchemaFixture.assertStringItems("\"completelyChanged\"", "\"preserve\"", "\"react\"");
    });
  }

  @Test
  public void testGuessType() {
    mySchemaFixture.baseInsertTest("guessType", "test");
  }

  @Test
  public void testDontGuessType() {
    mySchemaFixture.baseInsertTest("dontGuessType", "test");
  }

  @Test
  public void testDontInsertExtraValue() {
    mySchemaFixture.baseInsertTest("dontInsertExtraValue", "testWithValue");
  }

  @Test
  public void testDontInsertExtraValueColonOnly() {
    mySchemaFixture.baseInsertTest("dontInsertExtraValueColonOnly", "testWithValue");
  }

  @Test
  public void testPreserveColon() {
    mySchemaFixture.baseReplaceTest("preserveColon", "test");
  }

  @Test
  public void testEnumOrderSensitive() {
    mySchemaFixture.baseCompletionTest(mySchemaFixture.getTestName(true), "testValue", "\"c\"", "\"b\"", "\"a\"");
  }
}
