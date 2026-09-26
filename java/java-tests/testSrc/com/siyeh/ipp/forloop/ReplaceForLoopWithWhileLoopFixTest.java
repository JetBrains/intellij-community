// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.forloop;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.siyeh.ig.controlflow.ForLoopReplaceableByWhileInspection;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceForLoopWithWhileLoopFixTest extends IPPTestCase {

  public void testLabeledForLoop() { doTest(); }
  public void testNotInBlock() { doTest(); }
  public void testDoubleLabelNoBraces() { doTest(); }
  public void testUpdatingMuch() { doTest(); }
  public void testContinuing() { doTest(); }
  public void testConflictingNames() { doTest(); }
  public void testNoInit() { doTest(); }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", JavaKeywords.WHILE);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ForLoopReplaceableByWhileInspection());
  }

  @Override
  protected String getRelativePath() {
    return "forloop/while_loop";
  }
}