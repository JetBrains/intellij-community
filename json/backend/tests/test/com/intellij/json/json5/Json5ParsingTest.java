// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5;

import com.intellij.json.JsonParserDefinition;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/psi/")
public class Json5ParsingTest extends ParsingTestCase {
  public Json5ParsingTest() {
    super("psi", "json5", new Json5ParserDefinition(), new JsonParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData";
  }

  private void doTest() {
    doTest(true);
  }

  public void testJson5Syntax() {
    doTest();
  }
}
