// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.DefaultNotLastCaseInSwitchInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class MakeDefaultCaseLastFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new DefaultNotLastCaseInSwitchInspection()), "controlflow/makeDefaultLast",
                        "Make 'default' the last case", builder -> builder.setLanguageLevel(LanguageLevel.JDK_14));

  @Test
  public void testLabeledSwitchRule() { ig.get().doTest(); }
  @Test
  public void testOldStyleSwitchStatement() { ig.get().doTest(); }
  @Test
  public void testOldStyleSwitchStatementNoBreakThrough() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testOldStyleSwitchStatementNoBreakThrough1() { ig.get().assertQuickfixNotAvailable(); }
}