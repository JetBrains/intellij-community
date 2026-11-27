// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.LengthOneStringsInConcatenationInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@SuppressWarnings("SingleCharacterStringConcatenation")
@TestApplication
public class LengthOneStringsInConcatenationFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LengthOneStringsInConcatenationInspection()));

  @Test
  public void testFirstConcatenationOperand() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"f\"/**/ + \"bar\"", "'f' + \"bar\"");
  }

  @Test
  public void testSecondConcatenationOperand() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + /**/\"b\"", "\"foo\" + 'b'");
  }

  @Test
  public void testThirdConcatenationOperand() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + 1 + /**/\"c\"", "\"foo\" + 1 + 'c'");
  }

  @Test
  public void testAppendMethodParameter() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "new StringBuilder().append(/**/\"c\")", "new StringBuilder().append('c')");
  }

  @Test
  public void testNewLine() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\n\"/**/ + \"bar\"", "'\\n' + \"bar\"");
  }

  @Test
  public void testTextBlock() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"string\" + /**/\"\"\"\n    !\"\"\"", "\"string\" + '!'");
  }

  @Test
  public void testQuote() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\'\"/**/ + \"bar\"", "'\\'' + \"bar\"");
  }

  @Test
  public void testDoubleQuote() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\\"\"/**/ + \"bar\"", "'\"' + \"bar\"");
  }

  @Test
  public void testDoNotFixWrongType() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = /**/"a";
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixIfConcatenationTurnsIntoAddition() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = /**/"a" + 'b';
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixIfSecondOperandTurnsConcatenationIntoAddition() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               """
                                 class X {
                                   String field = 'a' + /**/"b";
                                 }
                                 """);
  }

}
