// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.cloneable;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.cloneable.CloneableImplementsCloneInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class CreateCloneMethodFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CloneableImplementsCloneInspection()), "cloneable/create_clone_method",
                        InspectionGadgetsBundle.message("cloneable.class.without.clone.quickfix"));

  @Test
  public void testFinal() { ig.get().assertQuickfixNotAvailable();}
  @Test
  public void testSimple() { ig.get().doTest();}
  @Test
  public void testNoThrows() { ig.get().doTest();}
  @Test
  public void testGenerics() { ig.get().doTest();}

}
