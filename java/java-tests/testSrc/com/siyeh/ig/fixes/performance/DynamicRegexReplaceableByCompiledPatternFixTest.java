// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.DynamicRegexReplaceableByCompiledPatternInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class DynamicRegexReplaceableByCompiledPatternFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "performance/replace_with_compiled_pattern",
                        InspectionGadgetsBundle.message("dynamic.regex.replaceable.by.compiled.pattern.quickfix"),
                        builder -> builder.addJdkVersion(LanguageLevel.JDK_1_8));

  private DynamicRegexReplaceableByCompiledPatternInspection myInspection;

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myInspection = new DynamicRegexReplaceableByCompiledPatternInspection();
    myFixture.enableInspections(myInspection);
  }

  @Test
  public void testLiteral() { ig.get().doTest(); }
  @Test
  public void testLiteralLiteral() { ig.get().doTest(); }
  @Test
  public void testReplaceAll() { ig.get().doTest(InspectionsBundle.message("fix.all.inspection.problems.in.file", myInspection.getDisplayName())); }

}
