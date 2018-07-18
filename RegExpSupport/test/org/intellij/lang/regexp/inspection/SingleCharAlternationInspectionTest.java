// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("RegExpRedundantEscape")
public class SingleCharAlternationInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    highlightTest("<warning descr=\"Single character alternation in RegExp\">a|b|c|d</warning>");
  }

  public void testNoWarn() {
    highlightTest("a|b|cc|d");
  }

  public void testNoWarnNoException() {
    highlightTest("(?i)x|y");
  }

  public void testQuickfix() {
    quickfixTest("<warning descr=\"Single character alternation in RegExp\">x|y|z</warning>", "[xyz]", "Replace with '[xyz]'");
  }

  public void testRemoveNonCapturingGroup() {
    quickfixTest("(?:<warning descr=\"Single character alternation in RegExp\">k<caret>|l|m</warning>)", "[klm]", "Replace with '[klm]'");
  }

  public void testRemoveEscaping() {
    quickfixTest("<warning descr=\"Single character alternation in RegExp\">\\^|\\å|\\{|\\\\|\\[</warning>", "[\\^å{\\\\\\[]", "Replace with '[\\^å{\\\\\\[]'");
  }

  public void testEscapes() {
    quickfixTest("(<warning descr=\"Single character alternation in RegExp\">\\.|<caret>\\[|]|\\(|\\)|\\{|}|\\^|\\?|\\*|\\||\\+|-|\\$</warning>)ab",
                 "([.\\[\\](){}^?*|+\\-$])ab", "Replace with '[.\\[\\](){}^?*|+\\-$]'");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new SingleCharAlternationInspection();
  }
}
