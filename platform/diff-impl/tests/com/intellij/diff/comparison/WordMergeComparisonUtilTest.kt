/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

class WordMergeComparisonUtilTest : ComparisonUtilTestBase() {
  fun testSimple() {
    words {
      ("" - "" - "")
      ("" - "" - "").default()
      testAll()
    }

    words {
      ("" - "X" - "")
      ("" - "-" - "").default()
      testAll()
    }

    words {
      ("X" - "" - "")
      ("-" - "" - "").default()
      testAll()
    }

    words {
      ("a b" - "a b" - "a b")
      ("   " - "   " - "   ").default()
      testAll()
    }

    words {
      ("A b c" - "a b c" - "a b C")
      ("-   -" - "-   -" - "-   -").default()
      testAll()
    }

    words {
      ("a c" - "a c" - "a X c")
      ("   " - "   " - " --  ").default()
      ("   " - "   " - "  -  ").ignore()
      testAll()
    }

    words {
      ("a X c" - "a X c" - "a c")
      (" --  " - " --  " - "   ").default()
      ("  -  " - "  -  " - "   ").ignore()
      testAll()
    }

    words {
      ("a X c" - "a c" - "a Y c")
      (" --  " - "   " - " --  ").default()
      ("  -  " - "   " - "  -  ").ignore()
      testAll()
    }

    words {
      ("a c" - "a X c" - "a Y c")
      ("   " - " --  " - " --  ").default()
      ("   " - "  -  " - "  -  ").ignore()
      testAll()
    }
  }

  fun testNewlines() {
    words {
      ("i" - "i_" - "_i")
      ("-" - "--" - "--").default() // TODO
      (" " - "  " - "  ").trim()
      testAll()
    }

    words {
      ("_i" - "i_" - "i")
      ("--" - "--" - "-").default()
      ("  " - "  " - " ").trim()
      testAll()
    }

    words {
      ("i" - "_i" - "i_")
      (" " - "- " - " -").default()
      (" " - "  " - "  ").trim()
      testAll()
    }

    words {
      ("_i" - "i" - "i_")
      ("- " - " " - " -").default()
      ("  " - " " - "  ").trim()
      testAll()
    }
  }
}