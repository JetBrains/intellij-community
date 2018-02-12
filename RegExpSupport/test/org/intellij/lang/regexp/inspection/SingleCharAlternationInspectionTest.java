/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
