// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.JavaFeature;
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
public class IfCanBePrimitivePatternSwitchFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    return List.of(inspection);
  },
    "migration/if_can_be_switch",
    CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH),
    builder -> { builder.setLanguageLevel(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()); builder.addJdkVersion(LanguageLevel.JDK_1_8); });

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testPrimitiveIntType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntTypeWithDifferent() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveIntTypeWithDifferentRevert() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveIntTypeRevert() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntTypeRevertComparison() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntTypeComparison() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongIntType() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveLongType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongType2() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongType3() { ig.get().doTest(); }

  @Test
  public void testPrimitiveIntegerNullType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntegerNullType2() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntegerNullType3() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntegerNotNullType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntegerNotNullType2() { ig.get().doTest(); }
  @Test
  public void testPrimitiveIntegerNotNullType3() { ig.get().doTest(); }

  @Test
  public void testPrimitiveLongObjectNullType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongObjectNullType2() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongObjectNullType3() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongObjectNotNullType() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongObjectNotNullType2() { ig.get().doTest(); }
  @Test
  public void testPrimitiveLongObjectNotNullType3() { ig.get().doTest(); }

  @Test
  public void testComparisonWithPrimitives1() { ig.get().doTest(); }
  @Test
  public void testComparisonWithPrimitives2() { ig.get().doTest(); }
  @Test
  public void testComparisonWithPrimitives3() { ig.get().doTest(); }
  @Test
  public void testComparisonWithPrimitivesUsedTwice() { ig.get().doTest(); }

  @Test
  public void testComparisonWithFloatingPoint() { ig.get().doTest(); }
  @Test
  public void testComparisonWithNaN() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testComparisonWithZero() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testComparisonWithNegativeZero() { ig.get().assertQuickfixNotAvailable(); }

  @Test
  public void testComparisonNonPrimitive() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testDoubleComparison() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveDominates() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testVariableAssigned() { ig.get().assertQuickfixNotAvailable(); }
}
