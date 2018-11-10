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
    )

    test(
      "x x x",
      "x x x",
      "x x x",
      "x x x"
    )

    test(
      "x x x",
      "x Y x",
      "x x x",
      "x Y x"
    )

    test(
      "x x",
      "x x",
      "x Y x",
      "x Y x"
    )

    test(
      "x X x",
      "x x",
      "x X x",
      "x x"
    )
  }

  fun testSameModification() {
    test(
      "x x x",
      "x Y x",
      "x Y x",
      "x Y x"
    )

    test(
      "x x",
      "x Y x",
      "x Y x",
      "x Y x"
    )

    test(
      "x X x",
      "x x",
      "x x",
      "x x"
    )
  }

  fun testNonConflictingChanges() {
    test(
      "x x x",
      "x Y x x",
      "x x Z x",
      "x Y x Z x"
    )

    test(
      "x",
      "x Y",
      "Z x",
      "Z x Y"
    )

    test(
      "x x",
      "x",
      "Z x x",
      "Z x"
    )
  }

  fun testFailure() {
    test(
      "x x x",
      "x Y x",
      "x Z x",
      null
    )

    test(
      "x x",
      "x Y x",
      "x Z x",
      null
    )
  }

  fun testNonFailureConflicts() {
    testGreedy(
      "x X x",
      "x x",
      "x X Y x",
      "x Y x"
    )

    testGreedy(
      "x X x",
      "x x",
      "x Y X x",
      "x Y x"
    )

    testGreedy(
      "x X Y x",
      "x X x",
      "x Y x",
      "x x"
    )

    testGreedy(
      "x X Y Z x",
      "x X x",
      "x Z x",
      "x x"
    )

    testGreedy(
      "x A B C D E F G H K x",
      "x C F K x",
      "x A D H x",
      "x x"
    )
  }

  fun testConfusingConflicts() {
    // these cases might be a failure as well

    testGreedy(
      "x X x",
      "x x",
      "x Z x",
      "xZ x"
    )

    testGreedy(
      "x X X x",
      "x X Y X x",
      "x x",
      "x Y x"
    )

    testGreedy(
      "x X x",
      "x x",
      "x Y x",
      "xY x"
    )


    testGreedy(
      "x X X x",
      "x Y x",
      "x X Y x",
      "x Y x"
    )
  }

  fun testRegressions() {
    test(
      "i\n",
      "i",
      "\ni",
      "i",
      "i"
    )

    testSimple(
      "Y X Y",
      "Y C\nX\nC Y",
      "Y \nX\n Y",
      "Y \nC\nX\nC Y"
    )

    test(
      """
  public static class ChangeData {
    @NotNull public final ChangeKind kind;
    public final int otherPath;

    public ChangeData(@NotNull ChangeKind kind, int otherPath) {
      this.kind = kind;
      this.otherPath = otherPath;
    }

    public boolean isRename() {
      return kind.equals(ChangeKind.RENAMED_FROM) || kind.equals(ChangeKind.RENAMED_TO);
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, otherPath);
    }
  }

  private enum ChangeKind {
    MODIFIED((byte)0),
    RENAMED_FROM((byte)1),
    RENAMED_TO((byte)2);""",
      """
  public enum ChangeKind {
    NOT_CHANGED((byte)-1),
    MODIFIED((byte)0),
    ADDED((byte)1),
    REMOVED((byte)2);""",
      """
  public static class ChangeData {
  @NotNull public final ChangeKind kind;
  public final int otherPath;

  public ChangeData(@NotNull ChangeKind kind, int otherPath) {
    this.kind = kind;
    this.otherPath = otherPath;
  }

  public boolean isRename() {
    return kind.equals(ChangeKind.RENAMED_FROM) || kind.equals(ChangeKind.RENAMED_TO);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, otherPath);
  }
}

private enum ChangeKind {
  MODIFIED((byte)0),
  RENAMED_FROM((byte)1),
  RENAMED_TO((byte)2);""",
      """
  public enum ChangeKind {
    NOT_CHANGED((byte)-1),
  MODIFIED((byte)0),
  ADDED((byte)1),
  REMOVED((byte)2);"""
    )
  }

  private fun testSimple(base: String, left: String, right: String, expected: String?) {
    val simpleResult = MergeResolveUtil.tryResolve(left, base, right)
    assertEquals(expected, simpleResult)
  }

  private fun testGreedy(base: String, left: String, right: String, expected: String?) {
    val greedyResult = MergeResolveUtil.tryGreedyResolve(left, base, right)
    assertEquals(expected, greedyResult)
  }

  private fun test(base: String, left: String, right: String, expected: String?) {
    testSimple(base, left, right, expected)
    testGreedy(base, left, right, expected)
  }

  private fun test(base: String, left: String, right: String, expectedSimple: String?, expectedGreedy: String?) {
    testSimple(base, left, right, expectedSimple)
    testGreedy(base, left, right, expectedGreedy)
  }
}
