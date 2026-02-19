// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;

public class IfCanBePrimitivePatternSwitchFixTest extends IGQuickFixesTestCase {
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
    builder.setLanguageLevel(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel());
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testPrimitiveIntType() { doTest(); }
  public void testPrimitiveIntTypeWithDifferent() { assertQuickfixNotAvailable(); }
  public void testPrimitiveIntTypeWithDifferentRevert() { assertQuickfixNotAvailable(); }
  public void testPrimitiveIntTypeRevert() { doTest(); }
  public void testPrimitiveIntTypeRevertComparison() { doTest(); }
  public void testPrimitiveIntTypeComparison() { doTest(); }
  public void testPrimitiveLongIntType() { assertQuickfixNotAvailable(); }
  public void testPrimitiveLongType() { doTest(); }
  public void testPrimitiveLongType2() { doTest(); }
  public void testPrimitiveLongType3() { doTest(); }

  public void testPrimitiveIntegerNullType() { doTest(); }
  public void testPrimitiveIntegerNullType2() { doTest(); }
  public void testPrimitiveIntegerNullType3() { doTest(); }
  public void testPrimitiveIntegerNotNullType() { doTest(); }
  public void testPrimitiveIntegerNotNullType2() { doTest(); }
  public void testPrimitiveIntegerNotNullType3() { doTest(); }

  public void testPrimitiveLongObjectNullType() { doTest(); }
  public void testPrimitiveLongObjectNullType2() { doTest(); }
  public void testPrimitiveLongObjectNullType3() { doTest(); }
  public void testPrimitiveLongObjectNotNullType() { doTest(); }
  public void testPrimitiveLongObjectNotNullType2() { doTest(); }
  public void testPrimitiveLongObjectNotNullType3() { doTest(); }

  public void testComparisonWithPrimitives1() { doTest(); }
  public void testComparisonWithPrimitives2() { doTest(); }
  public void testComparisonWithPrimitives3() { doTest(); }
  public void testComparisonWithPrimitivesUsedTwice() { doTest(); }

  public void testComparisonNonPrimitive() { assertQuickfixNotAvailable(); }
  public void testDoubleComparison() { assertQuickfixNotAvailable(); }
  public void testPrimitiveDominates() { assertQuickfixNotAvailable(); }
  public void testVariableAssigned() { assertQuickfixNotAvailable(); }
}
