// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

public class JsonSchemaDocumentationTest extends JsonBySchemaDocumentationBaseTest {
  @Override
  protected String getBasePath() {
    return "/tests/testData/jsonSchema/documentation";
  }

  public void testSimple() throws Exception {
    doTest(true, "json");
  }

  public void testSecondLevel() throws Exception {
    doTest(true, "json");
  }

  public void testCheckEscaping() throws Exception {
    doTest(true, "json");
  }

  public void testWithDefinition() throws Exception {
    doTest(true, "json");
  }

  public void testWithTitleInDefinition() throws Exception {
    doTest(true, "json");
  }

  public void testHtmlDescription() throws Exception {
    doTest(true, "json");
  }
}
