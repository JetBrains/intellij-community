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

public class LineComparisonUtilTest extends ComparisonUtilTestBase {
  public void testEqualStrings() {
    TestData.lines("", "")
      ._Def_()
      .all();

    TestData.lines("x", "x")
      ._Def_()
      .all();

    TestData.lines("x_y_z_", "x_y_z_")
      ._Def_()
      .all();

    TestData.lines("_", "_")
      ._Def_()
      .all();

    TestData.lines(" x_y ", " x_y ")
      ._Def_()
      .all();
  }

  public void testTrivialCases() {
    TestData.lines("x_", "y_")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("x", "")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("", "x")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("x", "y")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("x_z", "y_z")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("z_x", "z_y")
      ._Def_(mod(1, 1, 1, 1))
      .all();

    TestData.lines("x", "x_")
      ._Def_(ins(1, 1, 1))
      .all();

    TestData.lines("x_", "x")
      ._Def_(del(1, 1, 1))
      .all();
  }

  public void testSimpleCases() {
    TestData.lines("x_z", "y_z")
      ._Def_(mod(0, 0, 1, 1))
      .all();

    TestData.lines("x_", "x_z")
      ._Def_(mod(1, 1, 1, 1))
      .all();

    TestData.lines("x_y", "n_m")
      ._Def_(mod(0, 0, 2, 2))
      .all();

    TestData.lines("x_y_z", "n_y_m")
      ._Def_(mod(0, 0, 1, 1), mod(2, 2, 1, 1))
      .all();

    TestData.lines("x_y_z", "n_k_y")
      ._Def_(mod(0, 0, 1, 2), del(2, 3, 1))
      .all();

    TestData.lines("x_y_z", "y")
      ._Def_(del(0, 0, 1), del(2, 1, 1))
      .all();

    TestData.lines("a_b_x", "x_m_n")
      ._Def_(del(0, 0, 2), ins(3, 1, 2))
      .all();
  }

  public void testEmptyLastLine() {
    TestData.lines("x_", "")
      ._Def_(del(0, 0, 1))
      .all();

    TestData.lines("", "x_")
      ._Def_(ins(0, 0, 1))
      .all();

    TestData.lines("x_", "x")
      ._Def_(del(1, 1, 1))
      .all();

    TestData.lines("x_", "x_z ")
      ._Def_(mod(1, 1, 1, 1))
      .all();
  }

  public void testWhitespaceOnlyChanges() {
    TestData.lines("x ", " x")
      ._Def_(mod(0, 0, 1, 1))
      ._Trim_()
      .all();

    TestData.lines("x \t", "\t x")
      ._Def_(mod(0, 0, 1, 1))
      ._Trim_()
      .all();

    TestData.lines("x_", "x ")
      ._Def_(mod(0, 0, 2, 1))
      ._Trim_(del(1, 1, 1))
      .all();

    TestData.lines(" x_y ", "x _ y")
      ._Def_(mod(0, 0, 2, 2))
      ._Trim_()
      .all();

    TestData.lines("x y ", "x  y")
      ._Def_(mod(0, 0, 1, 1))
      ._Ignore_()
      .all();

    TestData.lines("x y_x y_x y", "  x y  _x y  _x   y")
      ._Def_(mod(0, 0, 3, 3))
      ._Trim_(mod(2, 2, 1, 1))
      ._Ignore_()
      .all();
  }

  public void testAlgorithmSpecific() {
    TestData.lines("x_y_z_AAAAA", "AAAAA_x_y_z")
      ._Def_(del(0, 0, 3), ins(4, 1, 3))
      .all();

    TestData.lines("x_y_z", " y_ m_ n")
      ._Def_(mod(0, 0, 3, 3))
      ._Trim_(del(0, 0, 1), mod(2, 1, 1, 2))
      .all();

    TestData.lines("}_ }", " }")
      ._Def_(del(0, 0, 1))
      .def();

    TestData.lines("{_}", "{_ {_ }_}_x")
      ._Def_(ins(1, 1, 2), ins(2, 4, 1))
      .def();
  }

  public void testNonDeterministicCases() {
    TestData.lines("", "__")
      ._Def_(ins(1, 1, 2))
      .all();

    TestData.lines("__", "")
      ._Def_(del(1, 1, 2))
      .all();
  }

  public void testBigBlockShiftRegression() {
    TestData.lines(" X_  X", "  X_   X")
      ._Def_(mod(0, 0, 2, 2))
      .def();
  }

  public void testTwoStepCanTrimRegression() {
    TestData.lines("q__7_ 6_ 7", "_7")
      ._Def_(del(0, 0, 1), del(3, 2, 2))
      .def();
  }
}
