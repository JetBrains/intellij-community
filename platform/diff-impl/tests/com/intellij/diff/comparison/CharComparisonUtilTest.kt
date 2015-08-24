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

public class CharComparisonUtilTest extends ComparisonUtilTestBase {
  public void testEqualStrings() {
    TestData.chars("", "")
      ._______Def_("", "")
      .all();

    TestData.chars("x", "x")
      ._______Def_(" ", " ")
      .all();

    TestData.chars("x_y_z_", "x_y_z_")
      ._______Def_("      ", "      ")
      .all();

    TestData.chars("_", "_")
      ._______Def_(" ", " ")
      .all();

    TestData.chars("xxx", "xxx")
      ._______Def_("   ", "   ")
      .all();

    TestData.chars("xyz", "xyz")
      ._______Def_("   ", "   ")
      .all();

    TestData.chars(".x!", ".x!")
      ._______Def_("   ", "   ")
      .all();
  }

  public void testTrivialCases() {
    TestData.chars("x", "")
      ._______Def_("-", "")
      .all();

    TestData.chars("", "x")
      ._______Def_("", "-")
      .all();

    TestData.chars("x", "y")
      ._______Def_("-", "-")
      .all();

    TestData.chars("x_", "")
      ._______Def_("--", "")
      .____Ignore_("- ", "")
      .all();

    TestData.chars("", "x_")
      ._______Def_("", "--")
      .____Ignore_("", "- ")
      .all();

    TestData.chars("x_", "y_")
      ._______Def_("- ", "- ")
      .all();

    TestData.chars("_x", "_y")
      ._______Def_(" -", " -")
      .all();
  }

  public void testSimpleCases() {
    TestData.chars("xyx", "xxx")
      ._______Def_(" - ", " - ")
      .all();

    TestData.chars("xyyx", "xmx")
      ._______Def_(" -- ", " - ")
      .all();

    TestData.chars("xyy", "yyx")
      ._______Def_("-  ", "  -")
      .all();

    TestData.chars("x!!", "!!x")
      ._______Def_("-  ", "  -")
      .all();

    TestData.chars("xyx", "xx")
      ._______Def_(" - ", "  ")
      .all();

    TestData.chars("xx", "xyx")
      ._______Def_("  ", " - ")
      .all();

    TestData.chars("!...", "...!")
      ._______Def_("-   ", "   -")
      .all();
  }

  public void testWhitespaceChangesOnly() {
    TestData.chars(" x y z ", "xyz")
      ._______Def_("- - - -", "   ")
      .____Ignore_("       ", "   ")
      .all();

    TestData.chars("xyz", " x y z ")
      ._______Def_("   ", "- - - -")
      .____Ignore_("   ", "       ")
      .all();

    TestData.chars("x ", "x")
      ._______Def_(" -", " ")
      .____Ignore_("  ", " ")
      .all();

    TestData.chars("x", " x")
      ._______Def_(" ", "- ")
      .____Ignore_(" ", "  ")
      .all();

    TestData.chars(" x ", "x")
      ._______Def_("- -", " ")
      .____Ignore_("   ", " ")
      .all();

    TestData.chars("x", " x ")
      ._______Def_(" ", "- -")
      .____Ignore_(" ", "   ")
      .all();
  }

  public void testWhitespaceChanges() {
    TestData.chars(" x ", "z")
      ._______Def_("---", "-")
      .____Ignore_(" - ", "-")
      .all();

    TestData.chars("x", " z ")
      ._______Def_("-", "---")
      .____Ignore_("-", " - ")
      .all();

    TestData.chars(" x", "z\t")
      ._______Def_("--", "--")
      .____Ignore_(" -", "- ")
      .all();

    TestData.chars("x ", "\tz")
      ._______Def_("--", "--")
      .____Ignore_("- ", " -")
      .all();
  }

  public void testIgnoreInnerWhitespaces() {
    TestData.chars("x z y", "xmn")
      ._______Def_(" ----", " --")
      .____Ignore_("  ---", " --")
      .all();

    TestData.chars("x y z ", "x y m ")
      ._______Def_("    - ", "    - ")
      .all();

    TestData.chars("x y z", "x y m ")
      ._______Def_("    -", "    --")
      .____Ignore_("    -", "    - ")
      .all();

    TestData.chars(" x y z", " m y z")
      ._______Def_(" -    ", " -    ")
      .all();

    TestData.chars("x y z", " m y z")
      ._______Def_("-    ", "--    ")
      .____Ignore_("-    ", " -    ")
      .all();

    TestData.chars("x y z", "x m z")
      ._______Def_("  -  ", "  -  ")
      .all();

    TestData.chars("x y z", "x  z")
      ._______Def_("  -  ", "    ")
      .all();

    TestData.chars("x  z", "x m z")
      ._______Def_("    ", "  -  ")
      .all();

    TestData.chars("x  z", "x n m z")
      ._______Def_("    ", "  ---  ")
      .all();
  }

  public void testEmptyRangePositions() {
    TestData.chars("x y", "x zy")
      ._Def_(ins(2, 2, 1))
      .all();

    TestData.chars("x y", "xz y")
      ._Def_(ins(1, 1, 1))
      .all();

    TestData.chars("x y z", "x  z")
      ._Def_(del(2, 2, 1))
      .all();

    TestData.chars("x  z", "x m z")
      ._Def_(ins(2, 2, 1))
      .all();

    TestData.chars("xyx", "xx")
      ._Def_(del(1, 1, 1))
      .all();

    TestData.chars("xx", "xyx")
      ._Def_(ins(1, 1, 1))
      .all();

    TestData.chars("xy", "x")
      ._Def_(del(1, 1, 1))
      .all();

    TestData.chars("x", "xy")
      ._Def_(ins(1, 1, 1))
      .all();
  }

  public void testAlgorithmSpecific() {
    // This is a strange example: "ignore whitespace" produces lesser matching, than "Default".
    // This is fine, as the main goal of "ignore whitespaces" is to reduce 'noise' of diff, and 1 change is better than 3 changes
    // So we actually "ignore" whitespaces during comparison, rather than "mark all whitespaces as matched".
    TestData.chars("x   y   z", "xX      Zz")
      ._______Def_("    -    ", " -      - ")
      .____Ignore_("    -    ", " -------- ")
      .all();
  }

  public void testNonDeterministicCases() {
    TestData.chars("x", "  ")
      ._Def_(del(0, 0, 1))
      .ignore();

    TestData.chars("  ", "x")
      ._Def_(ins(0, 0, 1))
      ._Ignore_();

    TestData.chars("x .. z", "x y .. z")
      ._______Def_("      ", "  --    ")
      .____Ignore_("      ", "  -     ")
      ._Def_(ins(2, 2, 2))
      ._Ignore_(ins(2, 2, 1))
      .all();

    TestData.chars(" x _ y _ z ", "x z")
      ._______Def_("-  ------ -", "   ")
      .____Ignore_("     -     ", "   ")
      ._Def_(del(0, 0, 1), del(3, 2, 6), del(10, 3, 1))
      ._Ignore_(del(5, 2, 1))
      .all();

    TestData.chars("x z", " x _ y _ z ")
      ._______Def_("   ", "-  ------ -")
      .____Ignore_("   ", "     -     ")
      ._Def_(ins(0, 0, 1), ins(2, 3, 6), ins(3, 10, 1))
      ._Ignore_(ins(2, 5, 1))
      .all();
  }
}
