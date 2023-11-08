// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.unicode;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.unicode.ReplaceOctalEscapeWithUnicodeEscapeIntention
 */
public class ReplaceOctalEscapeWithUnicodeUnescapeIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }
  public void testSelection() { doTest(); }
  public void testSelectionSingleSlash() { assertIntentionNotAvailable(); }
  public void testSelectionIncomplete() { doTest(); }
  public void testSelection2() { doTest(); }
  public void testStringTemplate() { doTest(); }
  public void testOtherEscape() { assertIntentionNotAvailable(); }

  @Override
  protected String getRelativePath() {
    return "unicode/octal";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.name");
  }
}