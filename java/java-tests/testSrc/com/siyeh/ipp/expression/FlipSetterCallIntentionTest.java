// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see FlipSetterCallIntention
 */
public class FlipSetterCallIntentionTest extends IPPTestCase {
  public void testSimple() { doTest(); }
  public void testUnqualified() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testSelection() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("flip.setter.call.intention.family.name");
  }

  @Override
  protected String getRelativePath() {
    return "expression/flip_setter_call";
  }
}
