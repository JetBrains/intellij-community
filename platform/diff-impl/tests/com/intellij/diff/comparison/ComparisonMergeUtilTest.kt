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

class ComparisonMergeUtilTest : ComparisonMergeUtilTestBase() {
  fun testSimpleCases() {
    chars {
      ("" - "" - "")
      ("" - "" - "").matching()
      changes()
      test()
    }

    chars {
      ("xyz" - "xyz" - "xyz")
      ("   " - "   " - "   ").matching()
      changes()
      test()
    }

    chars {
      ("" - "" - "x")
      ("" - "" - "-").matching()
      changes(mod(0, 0, 0, 0, 0, 1))
      test()
    }

    chars {
      ("x" - "yx" - "x")
      (" " - "- " - " ").matching()
      test()
    }

    chars {
      ("x" - "xy" - "x")
      (" " - " -" - " ").matching()
      test()
    }

    chars {
      ("xyz" - "xYz" - "xyz")
      (" - " - " - " - " - ").matching()
      test()
    }

    chars {
      ("xyz" - "XyZ" - "xyz")
      ("- -" - "- -" - "- -").matching()
      test()
    }
  }

  fun testConflictTYpes() {
    chars {
      ("abcd" - "abcd" - "abXcd")
      ("    " - "    " - "  -  ").matching()
      test()
    }

    chars {
      ("abXcd" - "abcd" - "abXcd")
      ("  -  " - "    " - "  -  ").matching()
      test()
    }

    chars {
      ("abcd" - "abXcd" - "abXcd")
      ("    " - "  -  " - "  -  ").matching()
      test()
    }

    chars {
      ("abcd" - "abXcd" - "abcd")
      ("    " - "  -  " - "    ").matching()
      test()
    }

    chars {
      ("abcd" - "abXcd" - "abYcd")
      ("    " - "  -  " - "  -  ").matching()
      test()
    }

    chars {
      ("abXcd" - "abXcd" - "abYcd")
      ("  -  " - "  -  " - "  -  ").matching()
      test()
    }

    chars {
      ("abYcd" - "abXcd" - "abYcd")
      ("  -  " - "  -  " - "  -  ").matching()
      test()
    }

    chars {
      ("abYcd" - "abXcd" - "abZcd")
      ("  -  " - "  -  " - "  -  ").matching()
      test()
    }

    chars {
      ("abXcd" - "abcd" - "abYcd")
      ("  -  " - "    " - "  -  ").matching()
      test()
    }
  }

  fun testBoundaryConflicts() {
    chars {
      ("abcd" - "abcd" - "abcdx")
      ("    " - "    " - "    -").matching()
      test()
    }

    chars {
      ("abcd" - "abcdx" - "abcdx")
      ("    " - "    -" - "    -").matching()
      test()
    }

    chars {
      ("abcd" - "abcdx" - "abcdy")
      ("    " - "    -" - "    -").matching()
      test()
    }

    chars {
      ("abcdz" - "abcdx" - "abcdy")
      ("    -" - "    -" - "    -").matching()
      test()
    }

    chars {
      ("xabcd" - "abcd" - "abcd")
      ("-    " - "    " - "    ").matching()
      test()
    }

    chars {
      ("xabcd" - "yabcd" - "abcd")
      ("-    " - "-    " - "    ").matching()
      test()
    }

    chars {
      ("abcd" - "yabcd" - "abcd")
      ("    " - "-    " - "    ").matching()
      test()
    }
  }

  fun testMultipleChanges() {
    chars {
      ("XXbXcXXeX" - "XyXzXXnXkX" - "XqXXeXrXX")
      ("  - -  - " - " - -  - - " - " -  - -  ").matching()
      test()
    }

    chars {
      ("Ax" - "z" - "zA")
      ("--" - "-" - "--").matching()
      test()
    }

    chars {
      ("ayz" - "xyz" - "xyq")
      ("- -" - "- -" - "- -").matching()
      test()
    }
  }
}
