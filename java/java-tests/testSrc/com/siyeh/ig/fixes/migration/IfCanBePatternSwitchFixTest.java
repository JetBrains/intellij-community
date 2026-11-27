// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class IfCanBePatternSwitchFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    return List.of(inspection);
  },
    "migration/if_can_be_switch",
    CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH),
    builder -> { builder.setLanguageLevel(LanguageLevel.JDK_21); builder.addJdkVersion(LanguageLevel.JDK_1_8); });

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testPatternType(){ ig.get().doTest(); }
  @Test
  public void testPatternImplicitNullCheck(){ ig.get().doTest(); }
  @Test
  public void testPatternExplicitNullCheck(){ ig.get().doTest(); }
  @Test
  public void testPatternExplicitNullCheck2(){ ig.get().doTest(); }
  @Test
  public void testPatternDefault() { ig.get().doTest(); }
  @Test
  public void testPatternKeepVariable() { ig.get().doTest(); }
  @Test
  public void testPatternGuard1() { ig.get().doTest(); }
  @Test
  public void testPatternGuard2() { ig.get().doTest(); }
  @Test
  public void testPatternGuardCustomOrder() { ig.get().doTest(); }
  @Test
  public void testPatternToVariable() { ig.get().doTest(); }
  @Test
  public void testPatternToSwitchExpression() { ig.get().doTest(); }
  @Test
  public void testUnconditionalPattern(){ ig.get().doTest(); }
  @Test
  public void testUnconditionalReturn() { ig.get().doTest(); }
  @Test
  public void testStringConstantsWithNull() { ig.get().doTest(); }
  @Test
  public void testCastsReplacedWithPattern() { ig.get().doTest(); }
  @Test
  public void testMultipleCastedVariables() { ig.get().doTest(); }
  @Test
  public void testMutableCastedVariable() { ig.get().doTest(); }
  @Test
  public void testLeakScope() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testNullCast() { ig.get().doTest(); }
  @Test
  public void testNotDoubleCall() { ig.get().doTest(); }
  @Test
  public void testWhenCast() { ig.get().doTest(); }
  @Test
  public void testSeveralIfStatements() { ig.get().doTest(); }
  @Test
  public void testSeveralIfStatementsWithComments() { ig.get().doTest(); }
  @Test
  public void testIfOnClass() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testWithEnums() { ig.get().doTest(); }
  @Test
  public void testIfWithDefaultNotNull() { ig.get().doTest(); }
}
