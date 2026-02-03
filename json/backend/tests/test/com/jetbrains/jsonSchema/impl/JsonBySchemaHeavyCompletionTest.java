// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JsonBySchemaHeavyCompletionTest extends JsonBySchemaHeavyCompletionTestBase {
  @Override
  protected String getExtensionWithoutDot() {
    return "json";
  }

  @Override
  protected String getBasePath() {
    return "/tests/testData/jsonSchema/completion";
  }

  public void testInsertEnumValue() throws Exception {
    baseInsertTest(getTestName(true), "testValue");
  }

  public void testInsertPropertyName() throws Exception {
    baseInsertTest("insertPropertyName", "testName");
  }

  public void testInsertNameWithDefaultStringValue() throws Exception {
    baseInsertTest("insertPropertyName", "testNameWithDefaultStringValue");
  }

  public void testIncompleteNameWithDefaultStringValue() throws Exception {
    baseInsertTest("insertPropertyName", "testIncompleteNameWithDefaultStringValue");
  }

  public void testInsertNameWithDefaultIntegerValue() throws Exception {
    baseInsertTest("insertPropertyName", "testNameWithDefaultIntegerValue");
  }

  public void testInsertIntegerType() throws Exception {
    baseInsertTest("insertPropertyName", "testIntegerType");
  }

  public void testInsertStringType() throws Exception {
    baseInsertTest("insertPropertyName", "testStringType");
  }

  public void testInsertObjectType() throws Exception {
    baseInsertTest("insertPropertyName", "testObjectType");
  }

  public void testInsertArrayType() throws Exception {
    baseInsertTest("insertPropertyName", "testArrayType");
  }

  public void testInsertBooleanType() throws Exception {
    baseInsertTest("insertPropertyName", "testBooleanType");
  }

  //no quotes
  public void testNameWithDefaultStringValueNoQuotes() throws Exception {
    baseInsertTest("insertPropertyName", "testNameWithDefaultStringValueNoQuotes");
  }

  public void testNameWithDefaultIntegerValueNoQuotesComma() throws Exception {
    baseInsertTest("insertPropertyName", "testNameWithDefaultIntegerValueNoQuotesComma");
  }

  //comma
  public void testInsertIntegerTypeComma() throws Exception {
    baseInsertTest("insertPropertyName", "testIntegerTypeComma");
  }

  public void testInsertBooleanTypeComma() throws Exception {
    baseInsertTest("insertPropertyName", "testBooleanTypeComma");
  }

  public void testStringTypeComma() throws Exception {
    baseInsertTest("insertPropertyName", "testStringTypeComma");
  }

  public void testNameWithDefaultStringValueComma() throws Exception {
    baseInsertTest("insertPropertyName", "testNameWithDefaultStringValueComma");
  }

  public void testWhitespaceAfterColon() throws Exception {
    baseInsertTest("addWhitespaceAfterColon", "colon");
  }

  public void testArrayLiteral() throws Exception {
    baseInsertTest("insertArrayOrObjectLiteral", "arrayLiteral");
    complete();
    assertStringItems("1", "2", "3");
  }

  public void testObjectLiteral() throws Exception {
    baseInsertTest("insertArrayOrObjectLiteral", "objectLiteral");
    complete();
    assertStringItems("\"insideTopObject1\"", "\"insideTopObject2\"");
  }

  public void testOneOfWithNotFilledPropertyValue() throws Exception {
    baseCompletionTest("oneOfWithEnumValue", "oneOfWithEmptyPropertyValue", "\"business\"", "\"home\"");
  }

  public void testRequiredPropsFirst() throws Exception {
    baseTestNoSchema("requiredProps", "requiredPropsFirst", () -> {
      complete();
      assertStringItems("a", "b");
    });
  }

  public void testRequiredPropsLast() throws Exception {
    baseTestNoSchema("requiredProps", "requiredPropsLast", () -> {
      complete();
      assertStringItems("b");
    });
  }

  public void testEditingSchemaAffectsCompletion() throws Exception {
    baseTest(getTestName(true), "testEditing", () -> {
      complete();
      assertStringItems("\"preserve\"", "\"react\"", "\"react-native\"");

      final PsiFile schema = myFixture.getFile().getParent().findFile("Schema.json");
      CommandProcessor.getInstance().runUndoTransparentAction(
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
      );
      VfsUtil.markDirtyAndRefresh(false, false, true, schema.getVirtualFile());
      schema.getVirtualFile().refresh(false, false);
      complete();
      assertStringItems("\"completelyChanged\"", "\"preserve\"", "\"react\"");
    });
  }

  public void testGuessType() throws Exception {
    baseInsertTest("guessType", "test");
  }

  public void testDontGuessType() throws Exception {
    baseInsertTest("dontGuessType", "test");
  }

  public void testDontInsertExtraValue() throws Exception {
    baseInsertTest("dontInsertExtraValue", "testWithValue");
  }

  public void testDontInsertExtraValueColonOnly() throws Exception {
    baseInsertTest("dontInsertExtraValueColonOnly", "testWithValue");
  }

  public void testPreserveColon() throws Exception {
    baseReplaceTest("preserveColon", "test");
  }

  public void testEnumOrderSensitive() throws Exception {
    baseCompletionTest(getTestName(true), "testValue", "\"c\"", "\"b\"", "\"a\"");
  }
}
