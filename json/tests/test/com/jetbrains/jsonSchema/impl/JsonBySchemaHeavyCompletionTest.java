// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.Assert;

/**
 * @author Irina.Chernushina on 3/4/2017.
 */
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

  public void testArrayLiteral() throws Exception {
    baseInsertTest("insertArrayOrObjectLiteral", "arrayLiteral");
    complete();
    assertStringItems("1","2","3");
  }

  public void testObjectLiteral() throws Exception {
    baseInsertTest("insertArrayOrObjectLiteral", "objectLiteral");
    complete();
    assertStringItems("\"insideTopObject1\"","\"insideTopObject2\"");
  }

  public void testOneOfWithNotFilledPropertyValue() throws Exception {
    baseCompletionTest("oneOfWithEnumValue", "oneOfWithEmptyPropertyValue", "\"business\"", "\"home\"");
  }

  public void testEditingSchemaAffectsCompletion() throws Exception {
    baseTest(getTestName(true), "testEditing", () -> {
      complete();
      assertStringItems("\"preserve\"", "\"react\"", "\"react-native\"");

      final PsiFile schema = myFile.getParent().findFile("Schema.json");
      final int idx = schema.getText().indexOf("react-native");
      Assert.assertTrue(idx > 0);
      PsiElement element = schema.findElementAt(idx);
      element = element instanceof JsonStringLiteral ? element : PsiTreeUtil.getParentOfType(element, JsonStringLiteral.class);
      Assert.assertTrue(element instanceof JsonStringLiteral);

      final PsiFile dummy = PsiFileFactory.getInstance(myProject).createFileFromText("test.json", JsonFileType.INSTANCE,
                                                                                    "{\"a\": \"completelyChanged\"}");
      Assert.assertTrue(dummy instanceof JsonFile);
      final JsonValue top = ((JsonFile)dummy).getTopLevelValue();
      final JsonValue newLiteral = ((JsonObject)top).findProperty("a").getValue();

      PsiElement finalElement = element;
      WriteAction.run(() -> finalElement.replace(newLiteral));

      complete();
      assertStringItems("\"completelyChanged\"", "\"preserve\"", "\"react\"");
    });
  }


}
