// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceForEachLoopWithIndexedForLoopIntention
 * @see ReplaceForEachLoopWithOptimizedIndexedForLoopIntention
 */
public class ReplaceForEachLoopWithIndexedForLoopIntentionTest extends IPPTestCase {
  public void testLabeledForLoop() { doTest(); }
  public void testNormalForeachLoop() { doTest(); }
  public void testThisExpr() { doTest(); }
  public void testNewArray() { doTest(); }
  public void testNoNPE() { doTest(); }
  public void testFQList() { doTest(); }
  public void testBlockNeeded() { doTest(); }
  public void testBlockNeededLabeled() { doTest(); }
  public void testVar() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "forloop/indexed";
  }
}
