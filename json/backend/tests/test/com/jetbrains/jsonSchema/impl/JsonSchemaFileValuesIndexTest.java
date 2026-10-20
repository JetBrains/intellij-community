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

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.indexing.FileContentImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.impl.JsonCachedValues.ID_CACHE_KEY;
import static com.jetbrains.jsonSchema.impl.JsonCachedValues.OBSOLETE_ID_CACHE_KEY;
import static com.jetbrains.jsonSchema.impl.JsonCachedValues.URL_CACHE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonSchemaFileValuesIndexTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
  }

  @Test
  public void testEmpty() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  @Test
  public void testSimple() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/empty.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  @Test
  public void testValid() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/valid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-id", map.get(ID_CACHE_KEY));
    assertCacheNull(map.get(URL_CACHE_KEY));
  }

  @Test
  public void testValid2() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/valid2.json5").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertEquals("the-schema", map.get(URL_CACHE_KEY));
    assertCacheNull(map.get(ID_CACHE_KEY));
  }

  @Test
  public void testInvalid() throws IOException {
    final VirtualFile file = myFixture.configureByFile("indexing/invalid.json").getVirtualFile();
    Map<String, String> map = new JsonSchemaFileValuesIndex().getIndexer().map(FileContentImpl.createByFile(file));
    assertAllCacheNulls(map);
  }

  @Test
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
