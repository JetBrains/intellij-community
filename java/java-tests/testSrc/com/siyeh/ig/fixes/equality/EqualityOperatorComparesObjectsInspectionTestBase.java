// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.equality;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.fixes.EqualityToEqualsFix;

/**
 * @author Bas Leijdekkers
 */
public abstract class EqualityOperatorComparesObjectsInspectionTestBase extends IGQuickFixesTestCase {

  public void testEnumComparison() { assertQuickfixNotAvailable(); }
  public void testNullComparison() { assertQuickfixNotAvailable(); }
  public void testPrimitiveComparison() { assertQuickfixNotAvailable(); }
  public void testSimpleObjectComparison() { doTest(EqualityToEqualsFix.getFixName(false)); }
  public void testNegatedObjectComparison() { doTest(EqualityToEqualsFix.getFixName(true)); }
  public void testCompareThisInEqualsMethod() { assertQuickfixNotAvailable(); }
  public void testCompareSameQualifiedThisInEqualsMethod() { assertQuickfixNotAvailable(); }
  public void testCompareOtherQualifiedThisInEqualsMethod() { doTest(EqualityToEqualsFix.getFixName(false)); }
  public void testCompareFieldInEqualsMethod() { doTest(EqualityToEqualsFix.getFixName(false)); }
  public void testNullableLeftOperandComparison() { assertQuickfixNotAvailable(EqualityToEqualsFix.getFixName(false)); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ObjectEqualityInspection());
    myDefaultHint = "Replace";
    myRelativePath = "equality/replace_equality_with_equals";
  }
}
