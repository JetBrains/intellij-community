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

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * @author Irina.Chernushina on 2/17/2016.
 */
public class JsonSchemaPatternComparatorTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testPatterns() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(getProject());
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("tes*")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("tes*"), p("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("test"), p("*est")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("testwords"), p("test*words")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(p("testwords"), p("*test*words")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(p("*.abc"), p("*.cde")));
    Assert.assertEquals(ThreeState.UNSURE, comparator.isSimilar(p("*.abc"), p("start.*")));
    Assert.assertEquals(ThreeState.UNSURE, comparator.isSimilar(p("two*words"), p("circus")));
  }

  public void test2Files() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(getProject());
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(f("test"), f("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(f("./test"), f("test")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(f("../test"), f("test")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(f("other"), f("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(f("one/../one/two"), f("one/two")));
  }

  public void test2Dirs() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(getProject());
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), d("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d("./test"), d("test")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(d("../test"), d("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d(".."), d("test")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(d("another"), d("test")));

    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d("test/child"), d("test")));
  }

  public void testDirAndFile() {
    final JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(getProject());
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(d("test"), f("test")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), f("test/lower")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d("test"), f("./test/lower")));
    Assert.assertEquals(ThreeState.YES, comparator.isSimilar(d(".."), f("test/lower")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(d("one"), f("test/lower")));
    Assert.assertEquals(ThreeState.NO, comparator.isSimilar(d("one"), f("test")));
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
