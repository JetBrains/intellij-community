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

class LineComparisonUtilTest : ComparisonUtilTestBase() {
  fun testEqualStrings() {
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

  fun testTrivialCases() {
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

  fun testSimpleCases() {
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

  fun testEmptyLastLine() {
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

  fun testWhitespaceOnlyChanges() {
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

  fun testAlgorithmSpecific() {
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

  fun testNonDeterministicCases() {
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

  fun `test regression - shifted similar lines should be matched as a single change, not insertion-deletion`() {
    lines {
      (" X_  X" - "  X_   X")
      ("--_---" - "---_----").default()
      testDefault()
    }
  }

  fun `test prefer chunks bounded by empty line`() {
    lines {
      ("A_B_o_o_Y_Z_ _A_B_z_z_Y_Z" - "A_B_o_o_Y_Z_ _A_B_u_u_Y_Z_ _A_B_z_z_Y_Z")
      (" _ _ _ _ _ _ _ _ _ _ _ _ " - " _ _ _ _ _ _ _-_-_-_-_-_-_-_ _ _ _ _ _ ").default()
      testAll()
    }

    lines {
      ("A_B_o_o_Y_Z_ _A_B_ _ _Y_Z" - "A_B_o_o_Y_Z_ _A_B_u_u_Y_Z_ _A_B_ _ _Y_Z")
      (" _ _ _ _ _ _ _ _ _ _ _ _ " - " _ _ _ _ _ _ _ _ _-_-_-_-_-_-_-_ _ _ _ ").default()
      testAll()
    }

    lines {
      ("A_B_o_o_ _A_B_z_z" - "A_B_o_o_ _A_B_u_u_ _A_B_z_z")
      (" _ _ _ _ _ _ _ _ " - " _ _ _ _ _-_-_-_-_-_ _ _ _ ").default()
      testAll()
    }

    lines {
      ("o_o_Y_Z_ _z_z_Y_Z" - "o_o_Y_Z_ _u_u_Y_Z_ _z_z_Y_Z")
      (" _ _ _ _ _ _ _ _ " - " _ _ _ _ _-_-_-_-_-_ _ _ _ ").default()
      testAll()
    }
  }

  fun `test prefer chunks bounded by short line`() {
    lines {
      ("A====_B====_o====_o====_Y====_Z====_!_A====_B====_z====_z====_Y====_Z====" -
       "A====_B====_o====_o====_Y====_Z====_!_A====_B====_u====_u====_Y====_Z====_!_A====_B====_z====_z====_Y====_Z====")
      ("     _     _     _     _     _     _ _     _     _     _     _     _     " -
       "     _     _     _     _     _     _ _-----_-----_-----_-----_-----_-----_-_     _     _     _     _     _     ").default()
      testAll()
    }

    lines {
      ("A====_B====_o====_o====_Y====_Z====_ _A====_B====_!_!_Y====_Z====" -
       "A====_B====_o====_o====_Y====_Z====_ _A====_B====_u====_u====_Y====_Z====_ _A====_B====_!_!_Y====_Z====")
      ("     _     _     _     _     _     _ _     _     _ _ _     _     " -
       "     _     _     _     _     _     _ _-----_-----_-----_-----_-----_-----_-_     _     _ _ _     _     ").default()
      testAll()
    }

    lines {
      ("A====_B====_o====_o====_Y====_Z====_!_A====_B====_ _ _Y====_Z====" -
       "A====_B====_o====_o====_Y====_Z====_!_A====_B====_u====_u====_Y====_Z====_!_A====_B====_ _ _Y====_Z====")
      ("     _     _     _     _     _     _ _     _     _ _ _     _     " -
       "     _     _     _     _     _     _ _     _     _-----_-----_-----_-----_-_-----_-----_ _ _     _     ").default()
      testAll()
    }
  }

  fun `test prefer chunks bounded by empty line - using inserted content`() {
    lines {
      ("e_x_A_B_z" - "x_A_B_ _q_A_B_z")
      ("-_ _ _ _ " - " _ _ _-_-_-_-_ ").default()
      testAll()
    }
  }

  fun `test prefer smaller amount of chunks`() {
    lines() {
      ("X_A_X_Y_" - "X_Y_")
      ("-_-_ _ _" - " _ _").default()
      testAll()
    }

    lines() {
      (" __x___y" - "__y")
      ("-**-*__ " - "__ ").default()
      default(del(0, 0, 3))
      testAll()
    }

    lines {
      ("U======_X======_Y======_z======_X======_Y======_X======_U======_X======_z======" -
       "U======_Y======_X======_U======_X======")
      ("       _-------_-------_-------_-------_       _       _       _       _-------" -
       "       _       _       _       _       ").default()
      testAll()
    }
  }

  fun `test regression - can trim chunks after 'compareTwoSteps'`() {
    lines {
      ("q__7_ 6_ 7" - "_7")
      ("-*_ _--*--" - "_ ").default()
      testDefault()
    }
  }

  fun `test regression - can trim chunks after 'optimizeLineChunks'`() {
    lines {
      ("A=====_ B=====_ }_}_B=====_" - "A=====_ }_}_B=====_")
      ("      _-------_  _ _      _" - "      _  _ _      _").default()
      testAll()
    }
  }

  fun `test bad cases caused by 'compareTwoStep' logic`() {
    lines {
      ("x_!" - "!_x_y")
      ("-_ " - " _-_-").default()
      testAll()
    }

    lines {
      ("!_x_y" - "x_!")
      ("-_ _-" - " _-").default()
      testAll()
    }

    lines {
      ("x_! " - "!_x_y")
      ("-_--" - "-_-_-").default()
      ("-_  " - " _-_-").trim()
      testAll()
    }

    lines {
      ("!_x_y" - "x_! ")
      ("-_ _-" - " _--").default()
      testAll()
    }

    splitter() {
      ("M===_X===_Y===" - " Y===_X===_N")
      // TODO: default(mod(0, 0, 1, 1), mod(2, 2, 1, 1))
      default(mod(0, 0, 1, 1), mod(1, 1, 1, 1), mod(2, 2, 1, 1))
      testDefault()
    }
  }

  fun `test bad cases caused by 'compareSmart' logic`() {
    lines {
      ("A=====_ B=====_ }_}_B=====" - "A=====_ }_}_B=====")
      // TODO ("      _-------_  _ _      " - "      _  _ _      ").trim()
      ("      _-------_  _ _      " - "      _  _ _      ").default()
      ("      _       _--_-_------" - "      _--_-_      ").trim()
      testAll()
    }

    lines {
      ("A=====_ B=====_X_ }_}_Z_B=====_" - "A=====_ }_}_B=====_")
      // TODO ("      _-------_-_  _ _-_      _" - "      _  _ _      _").default()
      ("      _-------_-_--_-_-_      _" - "      _--_-_      _").default()
      testAll()
    }
  }

  fun `test trim changed blocks after second step correction`() {
    lines() {
      ("====}_==== }_Y_====}" - "====}_Y_====}")
      ("     _------_ _     " - "     _ _     ").default() // result after second step correction
      ("     _      _-_-----" - "     _-_     ").ignore()  // result looks strange because of 'diff.unimportant.line.char.count'
      testAll()
    }
  }

  fun `test second step correction processes all confusing lines`() {
    lines {
      ("====}_==== }_Y_==== }_====}" - "==== }_Y_==== }")
      ("-----_      _ _      _-----" - "      _ _      ").default()
      testDefault()
    }
  }

  fun `test ignore whitespace policy does not applies two-step correction`() {
    lines() {
      ("1_ _  1" - "  1")
      ("-_-_   " - "   ").default()
      (" _-_---" - "   ").trim()
      testAll()
    }

    lines() {
      ("  1_ _1" - "  1")
      ("   _-_-" - "   ").default()
      testAll()
    }

    lines() {
      ("X_ Y_X" - "Y ")
      ("-_--_-" - "--").default()
      ("-_  _-" - "  ").trim()
      testAll()
    }
  }

  fun `test regression - second step correction should be performed if there are no ambiguous matchings`() {
    lines {
      ("}_ }" - " }_}")
      ("-_--" - "--_-").default()
      (" _  " - "  _ ").trim()
      testAll()
    }

    lines {
      (" }_}_ }" - "}_}_}")
      ("--_ _--" - "-_ _-").default()
      ("  _ _  " - " _ _ ").trim()
      testAll()
    }

    lines() {
      ("X_X __Y" - "X__Z")
      (" _--__-" - " __-").default()
      ("-_  __-" - " __-").trim()
      testAll()
    }
  }

  fun `test regression - second step with too many possible matchings`() {
    lines {
      (" X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_ X" - "X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X_X ")
      ("--_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _-_-_-_-_-_--" - "-_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _--").default()
      ("  _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _-_-_-_-_--" - " _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _  ").trim()
      testAll()
    }
  }

  fun `test regression - second step correction should search for matchings in its prefix`() {
    lines {
      ("Z_X_Z_X __Y" - "X__Y")
      ("-_ _-_--__ " - " __ ").default()
      ("-_-_-_  __ " - " __ ").trim()
      testAll()
    }

    // do not break other IW-matchings during second step
    lines {
      ("Z_X_K_Z_X __Y" - "K _X__Y")
      ("-_-_-_-_--__ " - "--_-__ ").default()
      ("-_-_ _-_  __ " - "  _ __ ").trim()
      testAll()
    }
  }

  fun `test regression - multiple similar braces nearby`() {
    // IDEA-111712
    lines {
      ("function fn() {_  return {_    a: 1_  }_}_" -
       "function fn() {_  return {_    a: 1_  };_}_")
      ("               _          _        _---_ _" -
       "               _          _        _----_ _").default()
      testAll()
    }

    lines {
      ("function fn() {_  return {_    a: 1_  }_}_" -
       "function fn() {_  return {_    a: 1_  }_};_")
      ("               _          _        _   _-_" -
       "               _          _        _   _--_").default()
      testAll()
    }
  }
}
