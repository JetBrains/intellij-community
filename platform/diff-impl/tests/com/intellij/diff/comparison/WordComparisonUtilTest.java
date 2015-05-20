/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison;

public class WordComparisonUtilTest extends ComparisonUtilTestBase {
  public void testSimpleCases() {
    TestData.words("x z", "y z")
      ._______Def_("-  ", "-  ")
      .all();

    TestData.words(" x z", "y z")
      ._______Def_("--  ", "-  ")
      .______Trim_(" -  ", "-  ")
      .all();

    TestData.words("x z ", "y z")
      ._______Def_("-  -", "-  ")
      .______Trim_("-   ", "-  ")
      .all();

    TestData.words("x z ", "y z")
      ._______Def_("-  -", "-  ")
      .______Trim_("-   ", "-  ")
      .all();

    TestData.words("x z", " y z ")
      ._______Def_("-  ", "--  -")
      .______Trim_("-  ", " -   ")
      .all();

    TestData.words("x y", "x z ")
      ._______Def_("  -", "  --")
      .______Trim_("  -", "  - ")
      .all();

    TestData.words("x,y", "x")
      ._______Def_(" --", " ")
      .all();

    TestData.words("x,y", "y")
      ._______Def_("-- ", " ")
      .all();

    TestData.words(".x=", ".!=")
      ._______Def_(" - ", " - ")
      .all();
  }

  public void testPunctuation() {
    TestData.words(" x.z.x ", "x..x")
      ._______Def_("-  -  -", "    ")
      .______Trim_("   -   ", "    ")
      .all();

    TestData.words("x..x", " x.z.x ")
      ._______Def_("    ", "-  -  -")
      .______Trim_("    ", "   -   ")
      .all();

    TestData.words("x ... z", "y ... z")
      ._______Def_("-      ", "-      ")
      .all();

    TestData.words("x ... z", "x ... y")
      ._______Def_("      -", "      -")
      .all();

    TestData.words("x ,... z", "x ... y")
      ._______Def_("  -    -", "      -")
      .all();

    TestData.words("x . , .. z", "x ... y")
      ._______Def_("   ---   -", "      -")
      .____Ignore_("    -    -", "      -")
      .all();

    TestData.words("x==y==z", "x====z")
      ._______Def_("   -   ", "      ")
      .all();

    TestData.words("x====z", "x==t==z")
      ._______Def_("      ", "   -   ")
      .all();
  }

  public void testOldDiffBug() {
    TestData.words("x'y'>", "x'>")
      ._______Def_("  -- ", "   ")
      .all();

    TestData.words("x'>", "x'y'>")
      ._______Def_("   ", "  -- ")
      .all();
  }

  public void testWhitespaceOnlyChanges() {
    TestData.words("x  =z", "x=  z")
      ._______Def_(" --  ", "  -- ")
      .skipIgnore();

    TestData.words("x  =", "x=  z")
      ._______Def_(" -- ", "  ---")
      .____Ignore_("    ", "    -")
      .all();
  }

  public void testNewlines() {
    TestData.words(" x _ y _ z ", "x z")
      ._______Def_("- ------  -", "   ")
      .______Trim_("     -     ", "   ")
      .____Ignore_("     -     ", "   ")
      .all();

    TestData.words("x z", " x _ y _ z ")
      ._______Def_("   ", "- ------  -")
      .______Trim_("   ", "     -     ")
      .____Ignore_("   ", "     -     ")
      .all();
  }

  public void testFixedBugs() {
    TestData.words(".! ", ".  y!")
      ._______Def_("  -", " --- ")
      .______Trim_("   ", " --- ")
      .____Ignore_("   ", "   - ")
      .all();

    TestData.words(" x n", " y_  x m")
      ._______Def_("   -", "----   -")
      .______Trim_("   -", " -     -")
      .____Ignore_("   -", " -     -")
      .all();

    TestData.words("x_", "x!  ")
      ._______Def_(" -", " ---")
      .______Trim_("  ", " -  ")
      .____Ignore_("  ", " -  ")
      .all();
  }

  public void testInnerWhitespaces() {
    TestData.words("<< x >>", "<.<>.>")
      ._______Def_("  ---  ", " -  - ")
      .____Ignore_("   -   ", " -  - ")
      .all();

    TestData.words("<< x >>", "y<<x>>y")
      ._______Def_("  - -  ", "-     -")
      .____Ignore_("       ", "-     -")
      .all();

    TestData.words("x .. z", "x y .. z")
      ._______Def_("      ", " --     ") // TODO: looks wrong
      .____Ignore_("      ", "  -     ")
      .all();

    TestData.words("  x..z", "x..y  ")
      ._______Def_("--   -", "   ---")
      .______Trim_("     -", "   -  ")
      .all();

    TestData.words(" x y x _ x z x ", "x x_x x")
      ._______Def_("- --  - - --  -", "       ")
      .______Trim_("  --      --   ", "       ")
      .____Ignore_("   -       -   ", "       ")
      .all();
  }

  public void testEmptyRangePositions() {
    // TODO
  }

  public void testNonDeterministicCases() {
    // TODO
  }

  public void testAlgorithmSpecific() {
    // prefer words over punctuation
    TestData.words("...x", "x...")
      ._______Def_("--- ", " ---")
      .all();

    // prefer longer words sequences
    TestData.words("x x y", "x y")
      ._______Def_("--   ", "   ")
      .____Ignore_("-    ", "   ")
      .all();

    // prefer punctuation over whitespaces
    TestData.words(".   ", "   .")
      ._______Def_(" ---", "--- ")
      .def();

    // TODO
  }
}
