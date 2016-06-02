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

import com.intellij.diff.DiffTestCase

class MergeResolveUtilTest : DiffTestCase() {
  fun testSimple() {
    test(
        "",
        "",
        "",
        ""
    );

    test(
        "x x x",
        "x x x",
        "x x x",
        "x x x"
    );

    test(
        "x x x",
        "x Y x",
        "x x x",
        "x Y x"
    );

    test(
        "x x",
        "x x",
        "x Y x",
        "x Y x"
    );

    test(
        "x X x",
        "x x",
        "x X x",
        "x x"
    );
  }

  fun testSameModification() {
    test(
        "x x x",
        "x Y x",
        "x Y x",
        "x Y x"
    );

    test(
        "x x",
        "x Y x",
        "x Y x",
        "x Y x"
    );

    test(
        "x X x",
        "x x",
        "x x",
        "x x"
    );
  }

  fun testNonConflictingChanges() {
    test(
        "x x x",
        "x Y x x",
        "x x Z x",
        "x Y x Z x"
    );

    test(
        "x",
        "x Y",
        "Z x",
        "Z x Y"
    );

    test(
        "x x",
        "x",
        "Z x x",
        "Z x"
    );
  }

  fun testFailure() {
    test(
        "x x x",
        "x Y x",
        "x Z x",
        null
    );

    test(
        "x x",
        "x Y x",
        "x Z x",
        null
    );
  }

  fun testNonFailureConflicts() {
    test(
        "x X x",
        "x x",
        "x X Y x",
        "x Y x"
    );

    test(
        "x X x",
        "x x",
        "x Y X x",
        "x Y x"
    );

    test(
        "x X Y x",
        "x X x",
        "x Y x",
        "x x"
    );

    test(
        "x X Y Z x",
        "x X x",
        "x Z x",
        "x x"
    );

    test(
        "x A B C D E F G H K x",
        "x C F K x",
        "x A D H x",
        "x x"
    );
  }

  fun testConfusingConflicts() {
    // these cases might be a failure as well

    test(
        "x X x",
        "x x",
        "x Z x",
        "xZ x"
    );

    test(
        "x X X x",
        "x X Y X x",
        "x x",
        "x Y x"
    );

    test(
        "x X x",
        "x x",
        "x Y x",
        "xY x"
    );


    test(
        "x X X x",
        "x Y x",
        "x X Y x",
        "x Y x"
    );
  }

  private fun test(base: String, left: String, right: String, expected: String?) {
    val actual = MergeResolveUtil.tryResolveConflict(left, base, right);
    assertEquals(expected, actual?.toString())
  }
}
