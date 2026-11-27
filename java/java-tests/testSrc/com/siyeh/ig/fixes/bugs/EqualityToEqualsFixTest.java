// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.NumberEqualityInspection;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class EqualityToEqualsFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ObjectEqualityInspection(), new NumberEqualityInspection()), "bugs/equality_to_equals");

  @Test
  public void testSimple() { ig.get().doTest(EqualityToEqualsFix.getFixName(false)); }
  @Test
  public void testPrecedence() { ig.get().doTest(EqualityToEqualsFix.getFixName(false)); }
  @Test
  public void testNegated() { ig.get().doTest(EqualityToEqualsFix.getFixName(true)); }

}
