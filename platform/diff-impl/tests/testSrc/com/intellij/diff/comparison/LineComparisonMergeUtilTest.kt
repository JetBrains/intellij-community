// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison

class LineComparisonMergeUtilTest : ComparisonMergeUtilTestBase() {
  fun testSimpleCases() {
    lines_merge {
      ("" - "" - "")
      ("" - "" - "").matching()
      changes()
      test()
    }

    lines_merge {
      ("x_y_z" - "x_y_z" - "x_y_z")
      (" _ _ " - " _ _ " - " _ _ ").matching()
      changes()
      test()
    }

    lines_merge {
      ("" - "" - "x")
      changes(mod(0, 0, 0, 1, 1, 1))
      test()
    }

    lines_merge {
      ("" - "" - "_x")
      ("" - "" - "_-").matching()
      changes(mod(1, 1, 1, 0, 0, 1))
      test()
    }

    lines_merge {
      ("x" - "y" - "x")
      ("-" - "-" - "-").matching()
      test()
    }

    lines_merge {
      ("x" - "x_y" - "x")
      (" " - " _-" - " ").matching()
      test()
    }

    lines_merge {
      ("x" - "y_x" - "x")
      (" " - "-_ " - " ").matching()
      test()
    }

    lines_merge {
      ("x_y_z" - "x_Y_z" - "x_y_z")
      (" _-_ " - " _-_ " - " _-_ ").matching()
      test()
    }

    lines_merge {
      ("x_y_z" - "X_y_Z" - "x_y_z")
      ("-_ _-" - "-_ _-" - "-_ _-").matching()
      test()
    }
  }

  fun testIgnoreWhitespaces() {
    lines_diff {
      ("x_ y _z" - "x_y_z" - "x_  y_  z")
      changes()
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      ("x_ y _z" - "x_y_z" - "x_  y_  z")
      changes(mod(1, 1, 1, 2, 2, 2))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    lines_diff {
      ("x_ y _z" - "x_Y_z" - "x_  y_  z")
      changes(mod(1, 1, 1, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      ("x_ y _z" - "x_Y_z" - "x_  y_  z")
      changes(mod(1, 1, 1, 1, 1, 1),
              mod(2, 2, 2, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    lines_merge {
      ("x_ y _z" - "x_Y_z" - "x_  y_z")
      changes(mod(1, 1, 1, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      ("x_ y _z" - "x_y_z" - "x_  y_z")
      changes(mod(1, 1, 1, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      (" x _y_z" - "x_y_z" - "x_y_  z")
      changes(mod(0, 0, 0, 1, 1, 1),
              mod(2, 2, 2, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    lines_diff {
      (" x_new_y_z" - "x_ y _z" - "x_y_new_ z")
      changes(mod(1, 1, 1, 1, 0, 0),
              mod(3, 2, 2, 0, 0, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      (" x_new_y_z" - "x_ y _z" - "x_y_new_ z")
      changes(mod(0, 0, 0, 1, 1, 1),
              mod(1, 1, 1, 1, 0, 0),
              mod(2, 1, 1, 1, 1, 1),
              mod(3, 2, 2, 0, 0, 1),
              mod(3, 2, 3, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    lines_diff {
      (" x_new_y1_z" - "x_ y _z" - "x_y2_new_ z")
      changes(mod(1, 1, 1, 2, 1, 2))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }

    lines_merge {
      (" x_new_y1_z" - "x_ y _z" - "x_y2_new_ z")
      changes(mod(0, 0, 0, 1, 1, 1),
              mod(1, 1, 1, 2, 1, 2),
              mod(3, 2, 3, 1, 1, 1))
      test(ComparisonPolicy.IGNORE_WHITESPACES)
    }
  }
}
