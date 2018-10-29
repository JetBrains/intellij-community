/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import static com.jetbrains.jsonSchema.impl.JsonCachedValues.ID_CACHE_KEY;
import static com.jetbrains.jsonSchema.impl.JsonCachedValues.URL_CACHE_KEY;

import com.intellij.json.JsonTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileContentImpl;
import java.util.Map;

public class JsonSchemaFileValuesIndexTest extends JsonTestCase {

  public void testEmpty() {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertTrue(map.isEmpty());
  }

  public void testSimple() {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertTrue(map.isEmpty());
  }

  public void testValid() {
    final VirtualFile file = myFixture.configureByFile("indexing/valid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-id", map.get(ID_CACHE_KEY));
    assertNull(map.get(URL_CACHE_KEY));
  }

  public void testValid2() {
    final VirtualFile file = myFixture.configureByFile("indexing/valid2.json5").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-schema", map.get(URL_CACHE_KEY));
    assertNull(map.get(ID_CACHE_KEY));
  }

  public void testInvalid() {
    final VirtualFile file = myFixture.configureByFile("indexing/invalid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertTrue(map.isEmpty());
  }

  public void testStopsOnAllFound() {
    final VirtualFile file = myFixture.configureByFile("indexing/duplicates.json5").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-schema", map.get(URL_CACHE_KEY));
    assertEquals("the-id", map.get(ID_CACHE_KEY));
  }
}
