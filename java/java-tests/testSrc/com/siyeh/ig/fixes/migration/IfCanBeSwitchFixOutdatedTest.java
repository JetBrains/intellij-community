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

@TestApplication
public class IfCanBeSwitchFixOutdatedTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 1;
    inspection.suggestIntSwitches = true;
    return List.of(inspection);
  },
    "migration/if_can_be_switch_outdated",
    CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH),
    builder -> { builder.setLanguageLevel(LanguageLevel.JDK_18_PREVIEW); builder.addJdkVersion(LanguageLevel.JDK_1_8); });

  @Test
  public void testWithGuard() { ig.get().assertQuickfixNotAvailable(); }

  @Test
  public void testSimple() { ig.get().doTest(); }
}
