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
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.ManualArrayToCollectionCopyInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ManualArrayToCollectionCopyFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ManualArrayToCollectionCopyInspection()), "performance/replace_with_add_all",
                        CommonQuickFixBundle.message("fix.replace.with.x", "Collections.addAll(...,...)"));

  @Test
  public void testSimpleFor() { ig.get().doTest(); }
  @Test
  public void testSimpleForeach() { ig.get().doTest(); }
  @Test
  public void testTempVarFor() { ig.get().doTest(); }

  @Test
  public void testPlusOne() { ig.get().doTest(); }
  @Test
  public void testPlusTwo() { ig.get().doTest(); }

  @Test
  public void testMinusOne() { ig.get().doTest(); }
  @Test
  public void testMinusTwo() { ig.get().doTest(); }

  @Test
  public void testMultiply() { ig.get().doTest(); }
  @Test
  public void testMultiplyLE() { ig.get().doTest(); }

  @Test
  public void testShift() { ig.get().doTest(); }
  @Test
  public void testShiftLE() { ig.get().doTest(); }

  @Test
  public void testUnary() { ig.get().doTest(); }
  @Test
  public void testUnary2() { ig.get().doTest(); }
  @Test
  public void testUnaryLE() { ig.get().doTest(); }

  @Test
  public void testForAtoB() { ig.get().doTest(); }
  @Test
  public void testPlusOneAtoB() { ig.get().doTest(); }
  @Test
  public void testPlusTwoAtoB() { ig.get().doTest(); }
  @Test
  public void testMinusOneAtoB() { ig.get().doTest(); }
  @Test
  public void testMinusTwoAtoB() { ig.get().doTest(); }

  @Test
  public void testPlusN() { ig.get().doTest(); }
  @Test
  public void testMinusN() { ig.get().doTest(); }
}
