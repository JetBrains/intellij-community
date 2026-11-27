/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.KeySetIterationMayUseEntrySetInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class KeySetIterationMayUseEntrySetFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new KeySetIterationMayUseEntrySetInspection()), "performance/key_set_with_entry_set",
                        CommonQuickFixBundle.message("fix.replace.with.x", "entrySet()"),
                        builder -> { builder.addJdkVersion(LanguageLevel.JDK_1_8); builder.setLanguageLevel(LanguageLevel.JDK_1_8); });

  @Test
  public void testSimple() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testParentheses() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testParenthesesAroundGetKey() { ig.get().doTest(); }
  @Test
  public void testCastNeeded1() { ig.get().doTest(); }
  @Test
  public void testCastNeeded2() { ig.get().doTest(); }
  @Test
  public void testReference() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testValues() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testSeveralKeys() { ig.get().doTest(); }
  @Test
  public void testWrongType() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testEntryIterationBug() { ig.get().doTest(); }
  @Test
  public void testLambda() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Map.forEach()")); }
  @Test
  public void testLambdaNoVar() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Map.forEach()")); }
  @Test
  public void testWildcardType() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  @Test
  public void testNestedLambdaNameConflict() { ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "entrySet()")); }

}