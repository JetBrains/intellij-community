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
package com.intellij.diff.comparison

public class LineComparisonUtilTest : ComparisonUtilTestBase() {
  public fun testEqualStrings() {
    lines {
      ("" - "")
      default()
      testAll()
    }

    lines {
      ("x" - "x")
      default()
      testAll()
    }

    lines {
      ("x_y_z_" - "x_y_z_")
      default()
      testAll()
    }

    lines {
      ("_" - "_")
      default()
      testAll()
    }

    lines {
      (" x_y " - " x_y ")
      default()
      testAll()
    }
  }

  public fun testTrivialCases() {
    lines {
      ("x_" - "y_")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("x" - "")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("" - "x")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("x" - "y")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("x_z" - "y_z")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("z_x" - "z_y")
      default(mod(1, 1, 1, 1))
      testAll()
    }

    lines {
      ("x" - "x_")
      default(ins(1, 1, 1))
      testAll()
    }

    lines {
      ("x_" - "x")
      default(del(1, 1, 1))
      testAll()
    }
  }

  public fun testSimpleCases() {
    lines {
      ("x_z" - "y_z")
      default(mod(0, 0, 1, 1))
      testAll()
    }

    lines {
      ("x_" - "x_z")
      default(mod(1, 1, 1, 1))
      testAll()
    }

    lines {
      ("x_y" - "n_m")
      default(mod(0, 0, 2, 2))
      testAll()
    }

    lines {
      ("x_y_z" - "n_y_m")
      default(mod(0, 0, 1, 1), mod(2, 2, 1, 1))
      testAll()
    }

    lines {
      ("x_y_z" - "n_k_y")
      default(mod(0, 0, 1, 2), del(2, 3, 1))
      testAll()
    }

    lines {
      ("x_y_z" - "y")
      default(del(0, 0, 1), del(2, 1, 1))
      testAll()
    }

    lines {
      ("a_b_x" - "x_m_n")
      default(del(0, 0, 2), ins(3, 1, 2))
      testAll()
    }
  }

  public fun testEmptyLastLine() {
    lines {
      ("x_" - "")
      default(del(0, 0, 1))
      testAll()
    }

    lines {
      ("" - "x_")
      default(ins(0, 0, 1))
      testAll()
    }

    lines {
      ("x_" - "x")
      default(del(1, 1, 1))
      testAll()
    }

    lines {
      ("x_" - "x_z ")
      default(mod(1, 1, 1, 1))
      testAll()
    }
  }

  public fun testWhitespaceOnlyChanges() {
    lines {
      ("x " - " x")
      default(mod(0, 0, 1, 1))
      trim()
      testAll()
    }

    lines {
      ("x \t" - "\t x")
      default(mod(0, 0, 1, 1))
      trim()
      testAll()
    }

    lines {
      ("x_" - "x ")
      default(mod(0, 0, 2, 1))
      trim(del(1, 1, 1))
      testAll()
    }

    lines {
      (" x_y " - "x _ y")
      default(mod(0, 0, 2, 2))
      trim()
      testAll()
    }

    lines {
      ("x y " - "x  y")
      default(mod(0, 0, 1, 1))
      ignore()
      testAll()
    }

    lines {
      ("x y_x y_x y" - "  x y  _x y  _x   y")
      default(mod(0, 0, 3, 3))
      trim(mod(2, 2, 1, 1))
      ignore()
      testAll()
    }
  }

  public fun testAlgorithmSpecific() {
    lines {
      ("x_y_z_AAAAA" - "AAAAA_x_y_z")
      default(del(0, 0, 3), ins(4, 1, 3))
      testAll()
    }

    lines {
      ("x_y_z" - " y_ m_ n")
      default(mod(0, 0, 3, 3))
      trim(del(0, 0, 1), mod(2, 1, 1, 2))
      testAll()
    }

    lines {
      ("}_ }" - " }")
      default(del(0, 0, 1))
      testDefault()
    }

    lines {
      ("{_}" - "{_ {_ }_}_x")
      default(ins(1, 1, 2), ins(2, 4, 1))
      testDefault()
    }
  }

  public fun testNonDeterministicCases() {
    lines {
      ("" - "__")
      default(ins(1, 1, 2))
      testAll()
    }

    lines {
      ("__" - "")
      default(del(1, 1, 2))
      testAll()
    }
  }

  public fun testBigBlockShiftRegression() {
    lines {
      (" X_  X" - "  X_   X")
      default(mod(0, 0, 2, 2))
      testDefault()
    }
  }

  public fun testPreferBoundedBlocks() {
    lines {
      ("A_B_o_o_Y_Z_ _A_B_z_z_Y_Z" - "A_B_o_o_Y_Z_ _A_B_u_u_Y_Z_ _A_B_z_z_Y_Z")
      default(ins(7, 7, 7))
      testAll()
    }

    lines {
      ("A_B_o_o_ _A_B_z_z" - "A_B_o_o_ _A_B_u_u_ _A_B_z_z")
      default(ins(5, 5, 5))
      testAll()
    }

    lines {
      ("o_o_Y_Z_ _z_z_Y_Z" - "o_o_Y_Z_ _u_u_Y_Z_ _z_z_Y_Z")
      default(ins(5, 5, 5))
      testAll()
    }
  }

  public fun testPreferLessBlocks() {
    lines() {
      ("X_A_X_Y_" - "X_Y_")
      default(del(0, 0, 2))
      testAll()
    }

    lines() {
      (" __x___y" - "__y")
      default(del(0, 0, 3))
      testAll()
    }

    lines {
      ("U======_X======_Y======_z======_X======_Y======_X======_U======_X======_z======" -
       "U======_Y======_X======_U======_X======")
      default(del(1, 1, 4), del(9, 5, 1))
      testAll()
    }
  }

  public fun testTwoStepCanTrimRegression() {
    lines {
      ("q__7_ 6_ 7" - "_7")
      default(del(0, 0, 1), del(3, 2, 2))
      testDefault()
    }
  }

  public fun `test trim changed blocks after second step correction`() {
    // ??? is there are a case with a range trimmed to zero during second step correction ?

    lines() {
      ("====}_==== }_Y_====}" - "====}_Y_====}")
      default(del(1, 1, 1)) // result after second step correction
      ignore(mod(1, 1, 2, 1)) // result looks strange because of 'diff.unimportant.line.char.count'
      testDefault()
      testTrim()
    }
  }
}
