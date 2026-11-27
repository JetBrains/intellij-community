// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class IfCanBeSwitchFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    inspection.suggestIntSwitches = true;
    return List.of(inspection);
  },
    "migration/if_can_be_switch",
    CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH),
    builder -> builder.addJdkVersion(LanguageLevel.JDK_1_8));

  @Test
  public void testComment() { ig.get().doTest();}
  @Test
  public void testComment2() { ig.get().doTest();}
  @Test
  public void testComments() { ig.get().doTest(); }
  @Test
  public void testLong() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPolyadic() { ig.get().doTest(); }
  @Test
  public void testStringEquality() { ig.get().doTest(); }
  @Test
  public void testObjectsEquals() { ig.get().doTest(); }
  @Test
  public void testEnumEquals() { ig.get().doTest(); }
  @Test
  public void testRelabelingBreaks() { ig.get().doTest(); }
  @Test
  public void testIfWithoutThenBranch() { ig.get().doTest(); }
  @Test
  public void testIfWithIsEmpty() { ig.get().doTest(); }
  @Test
  public void testIfWithIsEmptyInFirst() { ig.get().doTest(); }
}
