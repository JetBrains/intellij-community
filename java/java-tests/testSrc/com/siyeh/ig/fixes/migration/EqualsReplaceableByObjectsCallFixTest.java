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
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.EqualsReplaceableByObjectsCallInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class EqualsReplaceableByObjectsCallFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new EqualsReplaceableByObjectsCallInspection()), "migration/equals_replaceable_by_objects_call",
                        CommonQuickFixBundle.message("fix.replace.with.x", "Objects.equals()"),
                        builder -> builder.addJdkVersion(LanguageLevel.JDK_1_8) .setLanguageLevel(LanguageLevel.JDK_1_7));

  @Test
  public void testSimpleEquals() { ig.get().doTest(); }
  @Test
  public void testSimpleNotEquals() { ig.get().doTest(); }

  @Test
  public void testQualifiedArgument() { ig.get().doTest(); }
  @Test
  public void testQualifiedReciever() { ig.get().doTest(); }

  @Test
  public void testExpressionReciever() { ig.get().doTest(); }
  @Test
  public void testExpressionArgument() { ig.get().doTest(); }
  @Test
  public void testExpressionArgument2() { ig.get().doTest(); }

  @Test
  public void testLongEquals() { ig.get().doTest(); }
  @Test
  public void testLongNotEquals() { ig.get().doTest(); }
  @Test
  public void testShortEquals() { ig.get().doTest(); }
  @Test
  public void testShortNotEquals() { ig.get().doTest(); }

  @Test
  public void testSuperEquals() { ig.get().doTest(); }
  @Test
  public void testThisEquals() { ig.get().doTest(); }

  @Test
  public void testQualifiedThisNotEqual() { ig.get().doTest(); }
  @Test
  public void testQualifiedSuperEqual() { ig.get().doTest(); }

  @Test
  public void testTernaryEquals() { ig.get().doTest(); }
  @Test
  public void testTernaryNotEquals() { ig.get().doTest(); }

  @Test
  public void testStaticField() { ig.get().doTest(); }
  @Test
  public void testStaticField2() { ig.get().doTest(); }

  @Test
  public void testArrayAccessEquals() { ig.get().doTest(); }
  @Test
  public void testArrayAccessTernaryEquals() { ig.get().doTest(); }

}
