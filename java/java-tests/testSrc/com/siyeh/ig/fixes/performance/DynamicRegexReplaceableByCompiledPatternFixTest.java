// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.DynamicRegexReplaceableByCompiledPatternInspection;

public class DynamicRegexReplaceableByCompiledPatternFixTest extends IGQuickFixesTestCase {

  private DynamicRegexReplaceableByCompiledPatternInspection myInspection;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInspection = new DynamicRegexReplaceableByCompiledPatternInspection();
    myFixture.enableInspections(myInspection);
    myRelativePath = "performance/replace_with_compiled_pattern";
    myDefaultHint = InspectionGadgetsBundle.message("dynamic.regex.replaceable.by.compiled.pattern.quickfix");
  }

  public void testLiteral() { doTest(); }
  public void testLiteralLiteral() { doTest(); }
  public void testReplaceAll() { doTest(InspectionsBundle.message("fix.all.inspection.problems.in.file", myInspection.getDisplayName())); }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }
}
