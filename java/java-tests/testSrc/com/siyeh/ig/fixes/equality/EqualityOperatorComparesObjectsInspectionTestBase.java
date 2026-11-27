// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.equality;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public abstract class EqualityOperatorComparesObjectsInspectionTestBase {
  protected final TestFixture<IGQuickFixesFixture> ig;

  protected EqualityOperatorComparesObjectsInspectionTestBase(Consumer<JavaModuleFixtureBuilder<?>> tune) {
    ig = igQuickFixesFixture(() -> List.of(new ObjectEqualityInspection()), "equality/replace_equality_with_equals", "Replace", tune);
  }

  @Test
  public void testEnumComparison() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testNullComparison() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveComparison() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testSimpleObjectComparison() { ig.get().doTest(EqualityToEqualsFix.getFixName(false)); }
  @Test
  public void testNegatedObjectComparison() { ig.get().doTest(EqualityToEqualsFix.getFixName(true)); }
  @Test
  public void testCompareThisInEqualsMethod() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testCompareSameQualifiedThisInEqualsMethod() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testCompareOtherQualifiedThisInEqualsMethod() { ig.get().doTest(EqualityToEqualsFix.getFixName(false)); }
  @Test
  public void testCompareFieldInEqualsMethod() { ig.get().doTest(EqualityToEqualsFix.getFixName(false)); }
  @Test
  public void testNullableLeftOperandComparison() { ig.get().assertQuickfixNotAvailable(EqualityToEqualsFix.getFixName(false)); }
}
