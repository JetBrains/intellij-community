/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.settings.mappings.JsonSchemaPatternComparator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JsonSchemaPatternComparatorTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  @Test
  public void testPatterns() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(projectFixture.get());
    assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("tes*")));
    assertEquals(ThreeState.YES, comparator.isSimilar(p("tes*"), p("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("*est")));
    assertEquals(ThreeState.YES, comparator.isSimilar(p("testwords"), p("test*words")));
    assertEquals(ThreeState.YES, comparator.isSimilar(p("testwords"), p("*test*words")));
    assertEquals(ThreeState.NO, comparator.isSimilar(p("*.abc"), p("*.cde")));
    assertEquals(ThreeState.UNSURE, comparator.isSimilar(p("*.abc"), p("start.*")));
    assertEquals(ThreeState.UNSURE, comparator.isSimilar(p("two*words"), p("circus")));
  }

  @Test
  public void test2Files() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(projectFixture.get());
    assertEquals(ThreeState.YES, comparator.isSimilar(f("test"), f("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(f("./test"), f("test")));
    assertEquals(ThreeState.NO, comparator.isSimilar(f("../test"), f("test")));
    assertEquals(ThreeState.NO, comparator.isSimilar(f("other"), f("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(f("one/../one/two"), f("one/two")));
  }

  @Test
  public void test2Dirs() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(projectFixture.get());
    assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), d("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(d("./test"), d("test")));
    assertEquals(ThreeState.NO, comparator.isSimilar(d("../test"), d("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(d(".."), d("test")));
    assertEquals(ThreeState.NO, comparator.isSimilar(d("another"), d("test")));

    assertEquals(ThreeState.YES, comparator.isSimilar(d("test/child"), d("test")));
  }

  @Test
  public void testDirAndFile() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(projectFixture.get());
    assertEquals(ThreeState.NO, comparator.isSimilar(d("test"), f("test")));
    assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), f("test/lower")));
    assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), f("./test/lower")));
    assertEquals(ThreeState.YES, comparator.isSimilar(d(".."), f("test/lower")));
    assertEquals(ThreeState.NO, comparator.isSimilar(d("one"), f("test/lower")));
    assertEquals(ThreeState.NO, comparator.isSimilar(d("one"), f("test")));
  }

  @Test
  public void testCalculatedPatternFullMatch() {
    UserDefinedJsonSchemaConfiguration config = new UserDefinedJsonSchemaConfiguration(
      "test", null, "schema.json", false, java.util.List.of(p("composer.json"), p("dir/composer.json"))
    );
    var processors = config.getCalculatedPatterns();
    assertEquals(2, processors.size());
    var exactFile = new com.intellij.testFramework.LightVirtualFile("composer.json");
    var substringFile = new com.intellij.testFramework.LightVirtualFile("my-composer.json.bak");
    assertTrue(processors.getFirst().process(projectFixture.get(), exactFile));
    assertFalse(processors.getFirst().process(projectFixture.get(), substringFile));

    var exactPathFile = new com.intellij.testFramework.LightVirtualFile("composer.json") {
      @Override
      public @NotNull String getPath() {
        return "/project/dir/composer.json";
      }
    };
    var substringPathFile = new com.intellij.testFramework.LightVirtualFile("composer.json.bak") {
      @Override
      public @NotNull String getPath() {
        return "/project/dir/composer.json.bak";
      }
    };
    assertTrue(processors.get(1).process(projectFixture.get(), exactPathFile));
    assertFalse(processors.get(1).process(projectFixture.get(), substringPathFile));
  }

  private static UserDefinedJsonSchemaConfiguration.Item p(@NotNull final String p) {
    return new UserDefinedJsonSchemaConfiguration.Item(p, true, false);
  }

  private static UserDefinedJsonSchemaConfiguration.Item d(@NotNull final String d) {
    return new UserDefinedJsonSchemaConfiguration.Item(d, false, true);
  }

  private static UserDefinedJsonSchemaConfiguration.Item f(@NotNull final String f) {
    return new UserDefinedJsonSchemaConfiguration.Item(f, false, false);
  }
}
