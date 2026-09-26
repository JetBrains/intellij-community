// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.LengthOneStringsInConcatenationInspection;

/**
 * @author Fabrice TIERCELIN
 */
@SuppressWarnings("SingleCharacterStringConcatenation")
public class LengthOneStringsInConcatenationFixTest extends IGQuickFixesTestCase {
  public void testFirstConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"f\"/**/ + \"bar\"", "'f' + \"bar\"");
  }

  public void testSecondConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + /**/\"b\"", "\"foo\" + 'b'");
  }

  public void testThirdConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + 1 + /**/\"c\"", "\"foo\" + 1 + 'c'");
  }

  public void testAppendMethodParameter() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "new StringBuilder().append(/**/\"c\")", "new StringBuilder().append('c')");
  }

  public void testNewLine() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\n\"/**/ + \"bar\"", "'\\n' + \"bar\"");
  }

  public void testTextBlock() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"string\" + /**/\"\"\"\n    !\"\"\"", "\"string\" + '!'");
  }

  public void testQuote() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\'\"/**/ + \"bar\"", "'\\'' + \"bar\"");
  }

  public void testDoubleQuote() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\\"\"/**/ + \"bar\"", "'\"' + \"bar\"");
  }

  public void testDoNotFixWrongType() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = /**/"a";
                                 }
                                 """);
  }

  public void testDoNotFixIfConcatenationTurnsIntoAddition() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = /**/"a" + 'b';
                                 }
                                 """);
  }

  public void testDoNotFixIfSecondOperandTurnsConcatenationIntoAddition() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = 'a' + /**/"b";
                                 }
                                 """);
  }

  @Override
  protected BaseInspection getInspection() {
    return new LengthOneStringsInConcatenationInspection();
  }
}
