/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ConvertCatchToThrowsIntention
 */
public class ConvertCatchToThrowsTest extends IPPTestCase {
  public void testSingleCatch() { doTest(); }
  public void testPluralCatches() { doTest(); }
  public void testMultiCatch() { doTest(); }
  public void testArmWithPluralCatches() { doTest(); }
  public void testArmWithSingleCatch() { doTest(); }
  public void testExistingThrows() { doTest(); }
  public void testLambda() { doTest(); }
  public void testLeaveFinallySection() { doTest(); }
  public void testTryWithConflictingDeclaration() { doTest(); }
  public void testInLoop() { doTest(); }
  public void testInLoopSingleLine() { doTest(); }
  public void testInLoopSingleLineDeclaration() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/catchToThrows";
  }
}
