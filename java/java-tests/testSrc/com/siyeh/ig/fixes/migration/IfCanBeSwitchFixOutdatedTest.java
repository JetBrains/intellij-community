// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;

/**
 * @author Bas Leijdekkers
 */
public class IfCanBeSwitchFixOutdatedTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    final IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 1;
    inspection.suggestIntSwitches = true;
    myFixture.enableInspections(inspection);
    myRelativePath = "migration/if_can_be_switch_outdated";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.setLanguageLevel(LanguageLevel.JDK_18_PREVIEW);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testWithGuard() { assertQuickfixNotAvailable(); }

  public void testSimple() { doTest(); }
}
