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

import com.intellij.json.JsonTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileContentImpl;

import java.io.IOException;
import java.util.Map;

import static com.jetbrains.jsonSchema.impl.JsonCachedValues.*;

public class JsonSchemaFileValuesIndexTest extends JsonTestCase {

  public void testEmpty() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  public void testSimple() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  public void testValid() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/valid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-id", map.get(ID_CACHE_KEY));
    assertCacheNull(map.get(URL_CACHE_KEY));
  }

  public void testValid2() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/valid2.json5").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-schema", map.get(URL_CACHE_KEY));
    assertCacheNull(map.get(ID_CACHE_KEY));
  }

  public void testInvalid() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/invalid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  public void testStopsOnAllFound() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/duplicates.json5").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-schema", map.get(URL_CACHE_KEY));
    assertEquals("the-id", map.get(ID_CACHE_KEY));
    assertEquals("the-obsolete-id", map.get(OBSOLETE_ID_CACHE_KEY));
  }

  private static void assertCacheNull(String value) {
    assertEquals(JsonSchemaFileValuesIndex.NULL, value);
  }

  private static void assertAllCacheNulls(Map<String, String> map) {
    map.values().forEach(JsonSchemaFileValuesIndexTest::assertCacheNull);
  }
}
