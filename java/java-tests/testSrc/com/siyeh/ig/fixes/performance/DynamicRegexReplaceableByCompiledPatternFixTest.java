// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.DynamicRegexReplaceableByCompiledPatternInspection;

public class DynamicRegexReplaceableByCompiledPatternFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DynamicRegexReplaceableByCompiledPatternInspection());
    myRelativePath = "performance/replace_with_compiled_pattern";
    myDefaultHint = InspectionGadgetsBundle.message("dynamic.regex.replaceable.by.compiled.pattern.quickfix");
  }

  public void testLiteral() { doTest(); }
  public void testLiteralLiteral() { doTest(); }
  public void testReplaceAll() { doTest("Fix all 'Dynamic regular expression could be replaced by compiled 'Pattern'' problems in file"); }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }
}
