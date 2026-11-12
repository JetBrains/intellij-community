/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.trivialif;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class ConvertToNestedIfIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "trivialif/convert_to_nested_if", IntentionPowerPackBundle.message("convert.to.nested.if.intention.name"));

  @Test
  public void testNested() { ipp.get().doTest(); }
  @Test
  public void testStaircase() { ipp.get().doTest(); }
  @Test
  public void testNoBraces() { ipp.get().doTest(); }
  @Test
  public void testStaircaseWithParenthesis() { ipp.get().doTest(); }
  @Test
  public void testStaircaseWithOrInside() { ipp.get().doTest(); }
  @Test
  public void testOrStaircaseInside() { ipp.get().doTest(); }
  @Test
  public void testOneLevelStaircase() { ipp.get().assertIntentionNotAvailable(); }

  @Test
  public void testAndOrMixed() { ipp.get().doTest(); }
  @Test
  public void testOrAndMixed() { ipp.get().doTest(); }
}
