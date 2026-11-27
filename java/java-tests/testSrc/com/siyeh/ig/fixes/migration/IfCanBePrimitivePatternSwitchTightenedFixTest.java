// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class IfCanBePrimitivePatternSwitchTightenedFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    return List.of(inspection);
  },
    "migration/if_can_be_switch",
    CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH),
    builder -> { builder.setLanguageLevel(JavaFeature.PATTERNS_WITH_TIGHTENED_DOMINANCE.getMinimumLevel()); builder.addJdkVersion(LanguageLevel.JDK_1_8); });

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testEnumDomination() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testPrimitiveNonExactlyDomination() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testInterfaceDomination() { ig.get().assertQuickfixNotAvailable(); }
}
