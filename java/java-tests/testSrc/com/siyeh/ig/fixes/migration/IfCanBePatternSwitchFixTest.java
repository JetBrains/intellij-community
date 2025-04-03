// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;

public class IfCanBePatternSwitchFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    final IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    myFixture.enableInspections(inspection);
    myRelativePath = "migration/if_can_be_switch";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH);
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.setLanguageLevel(LanguageLevel.JDK_21);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testPatternType(){ doTest(); }
  public void testPatternImplicitNullCheck(){ doTest(); }
  public void testPatternExplicitNullCheck(){ doTest(); }
  public void testPatternExplicitNullCheck2(){ doTest(); }
  public void testPatternDefault() { doTest(); }
  public void testPatternKeepVariable() { doTest(); }
  public void testPatternGuard1() { doTest(); }
  public void testPatternGuard2() { doTest(); }
  public void testPatternGuardCustomOrder() { doTest(); }
  public void testPatternToVariable() { doTest(); }
  public void testPatternToSwitchExpression() { doTest(); }
  public void testUnconditionalPattern(){ doTest(); }
  public void testStringConstantsWithNull() { doTest(); }
  public void testCastsReplacedWithPattern() { doTest(); }
  public void testMultipleCastedVariables() { doTest(); }
  public void testMutableCastedVariable() { doTest(); }
  public void testLeakScope() { assertQuickfixNotAvailable(); }
  public void testNullCast() { doTest(); }
  public void testNotDoubleCall() { doTest(); }
  public void testWhenCast() { doTest(); }
  public void testSeveralIfStatements() { doTest(); }
  public void testSeveralIfStatementsWithComments() { doTest(); }
  public void testIfOnClass() { assertQuickfixNotAvailable(); }
  public void testWithEnums() { doTest(); }
  public void testIfWithDefaultNotNull() { doTest(); }
}
