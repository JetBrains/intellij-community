// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"RegExpDuplicateAlternationBranch", "RegExpEmptyAlternationBranch"})
public class DuplicateAlternationBranchInspectionTest extends RegExpInspectionTestCase {

  public void testSimple() {
    quickfixTest("<warning descr=\"Duplicate branch in alternation\">\\t<caret></warning>|<warning descr=\"Duplicate branch in alternation\">\\x09</warning>", "\\x09", "Remove duplicate branch");
  }

  public void testMoreBranches() {
    quickfixTest("<warning descr=\"Duplicate branch in alternation\">a{3}</warning>|<warning descr=\"Duplicate branch in alternation\">a<caret>{3,3}</warning>|b|c", "a{3}|b|c", "Remove duplicate branch");
  }

  public void testOrderIrrelevant() {
    highlightTest("<warning descr=\"Duplicate branch in alternation\">[abc]</warning>|<warning descr=\"Duplicate branch in alternation\">[cba]</warning>");
  }

  public void testEmptyBranches() {
    highlightTest("|||");
  }

  public void testNoWarn() {
    highlightTest("([aeiou][^aeiou])*|([^aeiou][aeiou])*");
  }

  public void testBrokenRange() {
    highlightTest("<warning descr=\"Duplicate branch in alternation\">[a-<error descr=\"Illegal character range\">\\</error>w]</warning>|<warning descr=\"Duplicate branch in alternation\">[a-<error descr=\"Illegal character range\">\\</error>w]</warning>");
  }

  @NotNull
  @Override
  protected LocalInspectionTool getInspection() {
    return new DuplicateAlternationBranchInspection();
  }
}
