// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import org.jetbrains.annotations.NotNull;

public class JsonEnterHandlingTest extends JsonTestCase {
  private void doTest(@NotNull final String before, @NotNull final String expected) {
    doTest(before, expected, "json");
  }

  private void doTest(@NotNull final String before, @NotNull final String expected, @NotNull final String extension) {
    myFixture.configureByText("test." + extension, before);
    myFixture.type('\n');
    myFixture.checkResult(expected);
  }

  public void testEnterAfterProperty() {
    doTest("{\"a\": true<caret>}", "{\"a\": true,\n}");
  }

  public void testEnterMidProperty() {
    doTest("{\"a\": tr<caret>ue}", "{\"a\": true,\n}");
  }

  public void testEnterMidObjectNoFollowing() {
    doTest("{\"a\": {<caret>}}", "{\"a\": {\n  \n}}");
  }

  public void testEnterMidObjectWithFollowing() {
    doTest("{\"a\": {<caret>} \"b\": 5}", "{\"a\": {\n  \n}, \"b\": 5}");
  }

  public void testEnterAfterObject() {
    doTest("{\"a\": {}<caret>}", "{\"a\": {},\n}");
  }
}
