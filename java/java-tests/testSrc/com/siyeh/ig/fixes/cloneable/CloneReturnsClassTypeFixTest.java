// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.cloneable;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.cloneable.CloneReturnsClassTypeInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class CloneReturnsClassTypeFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CloneReturnsClassTypeInspection ()), "cloneable/change_return_type");

  @Test
  public void testSimple() { ig.get().doTest("Change return type to 'Simple'"); }
  @Test
  public void testReturnNull() { ig.get().doTest("Change return type to 'ReturnNull'"); }
  @Test
  public void testTypeUseAnnotation() { ig.get().doTest("Change return type to 'TypeUseAnnotation'"); }
  @Test
  public void testCast() { ig.get().doTest("Change return type to 'Cast'"); }
  @Test
  public void testAnonymousClass() { ig.get().doTest("Change return type to 'B'"); }
  @Test
  public void testNested() { ig.get().doTest("Change return type to 'Nested'"); }

}
