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

class SplitComparisonUtilTest : ComparisonUtilTestBase() {
  fun testSplitter() {
    splitter {
      ("x" - "z")
      default(mod(0, 0, 1, 1))
      testAll()
    }
    splitter {
      ("x_y" - "a_b")
      default(mod(0, 0, 2, 2))
      testAll()
    }
    splitter {
      ("x_y" - "a_b y")
      default(mod(0, 0, 1, 1), mod(1, 1, 1, 1))
      testAll()
    }
    splitter {
      ("x y" - "x a_b y")
      default(mod(0, 0, 1, 2))
      testAll()
    }
    splitter {
      ("x_y" - "x a_y b")
      default(mod(0, 0, 1, 1), mod(1, 1, 1, 1))
      testAll()
    }

    splitter {
      ("x_" - "x a_...")
      default(mod(0, 0, 2, 2))
      testAll()
    }
    splitter {
      ("x_y_" - "a_b_")
      default(mod(0, 0, 2, 2))
      testAll()
    }
    splitter {
      ("x_y" - " x _ y ")
      default(mod(0, 0, 2, 2))
      testDefault()
    }
    splitter {
      ("x_y" - " x _ y.")
      default(mod(0, 0, 1, 1), mod(1, 1, 1, 1))
      testDefault()
    }

    splitter {
      ("a_x_b_" - " x_")
      default(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1))
      testDefault()
    }
    splitter {
      ("a_x_b_" - "!x_")
      default(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1))
      testAll()
    }
  }

  fun testSquash() {
    splitter(squash = true) {
      ("x" - "z")
      default(mod(0, 0, 1, 1))
      testAll()
    }
    splitter(squash = true) {
      ("x_y" - "a_b")
      default(mod(0, 0, 2, 2))
      testAll()
    }
    splitter(squash = true) {
      ("x_y" - "a_b y")
      default(mod(0, 0, 2, 2))
      testAll()
    }

    splitter(squash = true) {
      ("a_x_b_" - " x_")
      default(mod(0, 0, 3, 1))
      testDefault()
    }
    splitter(squash = true) {
      ("a_x_b_" - "!x_")
      default(mod(0, 0, 3, 1))
      testAll()
    }
  }

  fun testTrim() {
    splitter(trim = true) {
      ("_" - "     _    ")
      default(mod(0, 0, 2, 2))
      trim()
      testAll()
    }

    splitter(trim = true) {
      ("" - "     _    ")
      default(mod(0, 0, 1, 2))
      trim(ins(1, 1, 1))
      ignore()
      testAll()
    }

    splitter(trim = true) {
      ("     _    " - "")
      default(mod(0, 0, 2, 1))
      trim(del(1, 1, 1))
      ignore()
      testAll()
    }

    splitter(trim = true) {
      ("x_y" - "z_ ")
      default(mod(0, 0, 2, 2))
      testAll()
    }

    splitter(trim = true) {
      ("z" - "z_ ")
      default(ins(1, 1, 1))
      ignore()
      testAll()
    }

    splitter(trim = true) {
      ("z_ x" - "z_ w")
      default(mod(1, 1, 1, 1))
      testAll()
    }

    splitter(trim = true) {
      ("__z__" - "z")
      default(del(0, 0, 2), del(3, 1, 2))
      ignore()
      testAll()
    }
  }
}
