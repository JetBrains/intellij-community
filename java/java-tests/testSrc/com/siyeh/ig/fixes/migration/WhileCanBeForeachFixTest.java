// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.WhileCanBeForeachInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class WhileCanBeForeachFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new WhileCanBeForeachInspection()), "migration/while_can_be_foreach",
                        InspectionGadgetsBundle.message("foreach.replace.quickfix"),
                        builder -> builder.setLanguageLevel(LanguageLevel.HIGHEST));

  @Test
  public void testThis() { ig.get().doTest(); }
  @Test
  public void testCast() { ig.get().doTest(); }
  @Test
  public void testNakedNext() { ig.get().doTest(); }
  @Test
  public void testUnboundWildcard() { ig.get().doTest(); }
  @Test
  public void testVarWithoutValidInitializer() { ig.get().doTest(); }
  @Test
  public void testVarWithValidInitializer() { ig.get().doTest(); }
  @Test
  public void testRawIterator() { ig.get().doTest(); }
  @Test
  public void testUnboxing() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest(); }
  @Test
  public void testEmptyLoop() { ig.get().doTest(); }

}
