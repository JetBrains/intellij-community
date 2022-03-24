// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpSimplifiableInspectionTest extends RegExpInspectionTestCase {

  public void testRedundantRange() {
    doTest("[ah-hz]", 2, 3, "h", "[ahz]");
  }

  public void testNegatedDigit() {
    doTest("[^\\d]", "\\D");
  }

  public void testNegatedDigitRange() {
    doTest("[^0-9]", "\\D");
  }

  public void testNegatedWordClassCharExpression() {
    doTest("[^0-9a-zA-Z_]", "\\W");
  }

  public void testDigitRange() {
    doTest("[^0-9abc]", 2, 3, "\\d", "[^\\dabc]");
  }

  public void testDigitRange2() {
    doTest("[0-9abc]", 1, 3, "\\d", "[\\dabc]");
  }

  public void testSingleElementClass() {
    doTest("[a]", "a");
  }

  public void testNoWarnSingleElementClass() {
    highlightTest("[.]");
  }

  public void testSimpleDigitRange() {
    doTest("[0-9]", "\\d");
  }

  public void testWordCharClassExpression() {
    doTest("[0-9a-zA-Z_]", "\\w");
  }

  public void testStarToPlusNoWarm() {
    highlightTest("bba*c");
  }

  public void testStarToPlusNoWarn2() {
    highlightTest("b(a)(a)*c");
  }

  public void testStarToPlus() {
    doTest("baa*c", 1, 3, "a+", "ba+c");
  }

  public void testSingleRepetition() {
    quickfixTest("a<weak_warning descr=\"'{1}' is redundant\"><caret>{1}</weak_warning>",
                 "a", CommonQuickFixBundle.message("fix.remove", "{1}"));
  }

  public void testSimplifiableRange1() {
    doTest("a{0,1}", 1, 5, "?", "a?");
  }

  public void testSimplifiableRange2() {
    doTest("a{1,}", 1, 4, "+", "a+");
  }

  public void testSimplifiableRange3() {
    doTest("a{0,}", 1, 4, "*", "a*");
  }

  public void testFixedRepetitionRange() {
    doTest("a{3,3}", 1, 5, "{3}", "a{3}");
  }

  private void doTest(@Language("RegExp") String code, @Language("RegExp") String replacement) {
    doTest(code, 0, code.length(), replacement, replacement);
  }

  private void doTest(@Language("RegExp") String code, int offset, int length,
                      String replacement,
                      @Language("RegExp") String result) {
    final String suspect = code.substring(offset, offset + length);
    @Language("RegExp") final String warning =
      code.substring(0, offset) + "<weak_warning descr=\"'" + suspect + "' can be simplified to '" + replacement + "'\"><caret>" +
      suspect + "</weak_warning>" + code.substring(offset + length);
    quickfixTest(warning, result, CommonQuickFixBundle.message("fix.replace.with.x", replacement));
  }

  @Override
  protected @NotNull LocalInspectionTool getInspection() {
    return new RegExpSimplifiableInspection();
  }
}
