// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceConcatenationWithStringBufferIntention
 * @author Bas Leijdekkers
 */
public class ReplaceConcatenationWithStringBufferIntentionTest extends IPPTestCase {

  public void testNonStringConcatenationStart() { doTest(); }
  public void testConcatenationInsideAppend() { doTest(); }
  public void testConstantRequiredInsideAnnotationMethod() { assertIntentionNotAvailable(); }
  public void testConstantRequiredInSwitchCaseElement() { assertIntentionNotAvailable(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.concatenation.with.string.builder.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/string_builder";
  }
}
