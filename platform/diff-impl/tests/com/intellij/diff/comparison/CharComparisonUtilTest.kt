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

class CharComparisonUtilTest : ComparisonUtilTestBase() {
  fun testEqualStrings() {
    chars {
      ("" - "")
      ("" - "").default()
      testAll()
    }

    chars {
      ("x" - "x")
      (" " - " ").default()
      testAll()
    }

    chars {
      ("x_y_z_" - "x_y_z_")
      ("      " - "      ").default()
      testAll()
    }

    chars {
      ("_" - "_")
      (" " - " ").default()
      testAll()
    }

    chars {
      ("xxx" - "xxx")
      ("   " - "   ").default()
      testAll()
    }

    chars {
      ("xyz" - "xyz")
      ("   " - "   ").default()
      testAll()
    }

    chars {
      (".x!" - ".x!")
      ("   " - "   ").default()
      testAll()
    }
  }

  fun testTrivialCases() {
    chars {
      ("x" - "")
      ("-" - "").default()
      testAll()
    }

    chars {
      ("" - "x")
      ("" - "-").default()
      testAll()
    }

    chars {
      ("x" - "y")
      ("-" - "-").default()
      testAll()
    }

    chars {
      ("x_" - "")
      ("--" - "").default()
      ("- " - "").trim()
      testAll()
    }

    chars {
      ("" - "x_")
      ("" - "--").default()
      ("" - "- ").trim()
      testAll()
    }

    chars {
      ("x_" - "y_")
      ("- " - "- ").default()
      testAll()
    }

    chars {
      ("_x" - "_y")
      (" -" - " -").default()
      testAll()
    }
  }

  fun testSimpleCases() {
    chars {
      ("xyx" - "xxx")
      (" - " - " - ").default()
      testAll()
    }

    chars {
      ("xyyx" - "xmx")
      (" -- " - " - ").default()
      testAll()
    }

    chars {
      ("xyy" - "yyx")
      ("-  " - "  -").default()
      testAll()
    }

    chars {
      ("x!!" - "!!x")
      ("-  " - "  -").default()
      testAll()
    }

    chars {
      ("xyx" - "xx")
      (" - " - "  ").default()
      testAll()
    }

    chars {
      ("xx" - "xyx")
      ("  " - " - ").default()
      testAll()
    }

    chars {
      ("!..." - "...!")
      ("-   " - "   -").default()
      testAll()
    }
  }

  fun testWhitespaceChangesOnly() {
    chars {
      (" x y z " - "xyz")
      ("- - - -" - "   ").default()
      ("  - -  " - "   ").trim()
      ("       " - "   ").ignore()
      testAll()
    }

    chars {
      ("xyz" - " x y z ")
      ("   " - "- - - -").default()
      ("   " - "  - -  ").trim()
      ("   " - "       ").ignore()
      testAll()
    }

    chars {
      ("x " - "x")
      (" -" - " ").default()
      ("  " - " ").trim()
      testAll()
    }

    chars {
      ("x" - " x")
      (" " - "- ").default()
      (" " - "  ").trim()
      testAll()
    }

    chars {
      (" x " - "x")
      ("- -" - " ").default()
      ("   " - " ").trim()
      testAll()
    }

    chars {
      ("x" - " x ")
      (" " - "- -").default()
      (" " - "   ").trim()
      testAll()
    }
  }

  fun testWhitespaceChanges() {
    chars {
      (" x " - "z")
      ("---" - "-").default()
      (" - " - "-").trim()
      testAll()
    }

    chars {
      ("x" - " z ")
      ("-" - "---").default()
      ("-" - " - ").trim()
      testAll()
    }

    chars {
      (" x" - "z\t")
      ("--" - "-.-").default()
      (" -" - "-. ").trim()
      testAll()
    }

    chars {
      ("x " - "\tz")
      ("--" - ".--").default()
      ("- " - ". -").trim()
      testAll()
    }

    chars {
      ("x y z" - "xy_z")
      (" - - " - "  - ").default()
      (" - - " - "    ").trim()
      ("     " - "    ").ignore()
      testAll()
    }

    chars {
      ("x y \n z" - "xy\nz")
      (" - -. - " - "  .  ").default()
      (" -  .   " - "  .  ").trim()
      ("    .   " - "  .  ").ignore()
      testAll()
    }
  }

  fun testIgnoreInnerWhitespaces() {
    chars {
      ("x z y" - "xmn")
      (" ----" - " --").default()
      ("  ---" - " --").ignore()
      testAll()
    }

    chars {
      ("x y z " - "x y m ")
      ("    - " - "    - ").default()
      testAll()
    }

    chars {
      ("x y z" - "x y m ")
      ("    -" - "    --").default()
      ("    -" - "    - ").trim()
      testAll()
    }

    chars {
      (" x y z" - " m y z")
      (" -    " - " -    ").default()
      testAll()
    }

    chars {
      ("x y z" - " m y z")
      ("-    " - "--    ").default()
      ("-    " - " -    ").trim()
      testAll()
    }

    chars {
      ("x y z" - "x m z")
      ("  -  " - "  -  ").default()
      testAll()
    }

    chars {
      ("x y z" - "x  z")
      ("  -  " - "    ").default()
      testAll()
    }

    chars {
      ("x  z" - "x m z")
      ("    " - "  -  ").default()
      testAll()
    }

    chars {
      ("x  z" - "x n m z")
      ("    " - "  ---  ").default()
      testAll()
    }
  }

  fun testEmptyRangePositions() {
    chars {
      ("x y" - "x zy")
      default(ins(2, 2, 1))
      testAll()
    }

    chars {
      ("x y" - "xz y")
      default(ins(1, 1, 1))
      testAll()
    }

    chars {
      ("x y z" - "x  z")
      default(del(2, 2, 1))
      testAll()
    }

    chars {
      ("x  z" - "x m z")
      default(ins(2, 2, 1))
      testAll()
    }

    chars {
      ("xyx" - "xx")
      default(del(1, 1, 1))
      testAll()
    }

    chars {
      ("xx" - "xyx")
      default(ins(1, 1, 1))
      testAll()
    }

    chars {
      ("xy" - "x")
      default(del(1, 1, 1))
      testAll()
    }

    chars {
      ("x" - "xy")
      default(ins(1, 1, 1))
      testAll()
    }
  }

  fun testAlgorithmSpecific() {
    // This is a strange example: "ignore whitespace" produces lesser matching, than "Default".
    // This is fine, as the main goal of "ignore whitespaces" is to reduce 'noise' of diff, and 1 change is better than 3 changes
    // So we actually "ignore" whitespaces during comparison, rather than "mark all whitespaces as matched".
    chars {
      ("x   y   z" - "xX      Zz")
      ("    -    " - " -      - ").default()
      ("    -    " - " -------- ").ignore()
      testAll()
    }
  }

  fun testNonDeterministicCases() {
    chars {
      ("x" - "  ")
      ignore(del(0, 0, 1))
      testIgnore()
    }

    chars {
      ("  " - "x")
      ignore(ins(0, 0, 1))
      testIgnore()
    }

    chars {
      ("x .. z" - "x y .. z")
      ("      " - "  --    ").default()
      ("      " - "  -     ").ignore()
      default(ins(2, 2, 2))
      ignore(ins(2, 2, 1))
      testAll()
    }

    chars {
      (" x _ y _ z " - "x z")
      ("-  ------ -" - "   ").default()
      ("     -     " - "   ").trim()
      default(del(0, 0, 1), del(3, 2, 6), del(10, 3, 1))
      trim(del(5, 2, 1))
      testAll()
    }

    chars {
      ("x z" - " x _ y _ z ")
      ("   " - "-  ------ -").default()
      ("   " - "     -     ").trim()
      default(ins(0, 0, 1), ins(2, 3, 6), ins(3, 10, 1))
      trim(ins(2, 5, 1))
      testAll()
    }
  }

  fun testTwoSteps() {
    chars_smart {
      ("  a" - "a  ")
      ("-- " - " --").default()
      testDefault()
    }

    chars_raw {
      ("  a" - "a  ")
      ("  -" - "-  ").default()
      testDefault()
    }

    chars_smart {
      ("bba" - "abb")
      ("  -" - "-  ").default()
      testDefault()
    }

    chars_raw {
      ("bba" - "abb")
      ("  -" - "-  ").default()
      testDefault()
    }

    chars_smart {
      ("$chSmile$chFace $chMan" - " $chGun$chFace$chMan ")
      ("--  -  " - "---    -").default()
      testDefault()
    }

    chars_raw {
      ("$chSmile$chFace $chMan" - " $chGun$chFace$chMan ")
      ("--   --" - "---  -- ").default()
      testDefault()
    }
  }

  fun testHighSurrogates() {
    chars {
      (chSmile - chSmile)
      ("  " - "  ").default()
      testAll()
    }

    chars {
      (chSmile - chMan)
      ("--" - "--").default()
      testAll()
    }

    chars {
      (chSmile - chGun)
      ("--" - "--").default()
      testAll()
    }

    chars {
      (chFace - chGun)
      ("--" - "--").default()
      testAll()
    }

    chars {
      ("$chSmile$chGun$chMan$chFace" - "$chGun$chMan$chFace$chSmile")
      ("--      " - "      --").default()
      testAll()
    }

    chars {
      (chSmile - chGun)
      ("--" - "--").default()
      testAll()
    }

    chars {
      (chFace - chGun)
      ("--" - "--").default()
      testAll()
    }

    chars {
      ("$chSmile " - chGun)
      ("---" - "--").default()
      ("-- " - "--").trim()
      testAll()
    }

    chars {
      (" $chSmile$chFace $chMan" - "$chGun$chFace$chMan")
      ("---  -  " - "--    ").default()
      (" --  -  " - "--    ").trim()
      (" --     " - "--    ").ignore()
      testAll()
    }
  }
}
