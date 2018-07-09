// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.json5;

import com.jetbrains.jsonSchema.impl.JsonBySchemaCompletionBaseTest;

public class Json5ByJsonSchemaCompletionTest extends JsonBySchemaCompletionBaseTest {
  public void testTopLevel() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{pri<caret>}", "json5", "prima", "primus", "proto");
  }

  public void testAlreadyInsertedProperty() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{prima: 1, pri<caret>}", "json5", "primus", "proto");
  }
}
