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
package com.intellij.diff.merge

import com.intellij.diff.merge.MergeTestBase.SidesState.*
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType.*
import com.intellij.diff.util.ThreeSide

class MergeTest : MergeTestBase() {
  fun testChangeTypes() {
    test("", "", "", 0) {
    }

    test("x", "x", "x", 0) {
    }

    test("x_y_z a x", "x_y_z a x", "x_y_z a x", 0) {
    }

    test1("x", "x", "y") {
      0.assertType(MODIFIED, RIGHT)
      0.assertContent("x", 0, 1)
      0.assertResolved(NONE)
    }

    test1("x", "y", "x") {
      0.assertType(MODIFIED, BOTH)
      0.assertContent("y", 0, 1)
      0.assertResolved(NONE)
    }

    test2("x_Y", "Y", "Y_z") {
      0.assertType(INSERTED, LEFT)
      0.assertContent("", 0, 0)
      0.assertResolved(NONE)

      1.assertType(INSERTED, RIGHT)
      1.assertContent("", 1, 1)
      1.assertResolved(NONE)
    }

    test2("Y_z", "x_Y_z", "x_Y") {
      0.assertType(DELETED, LEFT)
      0.assertContent("x", 0, 1)
      0.assertResolved(NONE)

      1.assertType(DELETED, RIGHT)
      1.assertContent("z", 2, 3)
      1.assertResolved(NONE)
    }

    test1("X_Z", "X_y_Z", "X_Z") {
      0.assertType(DELETED, BOTH)
      0.assertContent("y", 1, 2)
      0.assertResolved(NONE)
    }

    test1("X_y_Z", "X_Z", "X_y_Z") {
      0.assertType(INSERTED, BOTH)
      0.assertContent("", 1, 1)
      0.assertResolved(NONE)
    }

    test1("x", "y", "z") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("y", 0, 1)
      0.assertResolved(NONE)
    }

    test1("z_Y", "x_Y", "Y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("x", 0, 1)
      0.assertResolved(NONE)
    }

    test1("z_Y", "x_Y", "k_x_Y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("x", 0, 1)
      0.assertResolved(NONE)
    }

    test1("x_Y", "Y", "z_Y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("", 0, 0)
      0.assertResolved(NONE)
    }

    test1("x_Y", "Y", "z_x_Y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("", 0, 0)
      0.assertResolved(NONE)
    }

    test1("x_Y", "x_z_Y", "z_Y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("x_z", 0, 2)
      0.assertResolved(NONE)
    }
  }

  fun testLastLine() {
    test1("x", "x_", "x") {
      0.assertType(DELETED, BOTH)
      0.assertContent("", 1, 2)
      0.assertResolved(NONE)
    }

    test1("x_", "x_", "x") {
      0.assertType(DELETED, RIGHT)
      0.assertContent("", 1, 2)
      0.assertResolved(NONE)
    }

    test1("x_", "x_", "x_y") {
      0.assertType(MODIFIED, RIGHT)
      0.assertContent("", 1, 2)
      0.assertResolved(NONE)
    }

    test1("x", "x_", "x_y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("", 1, 2)
      0.assertResolved(NONE)
    }

    test1("x_", "x", "x_y") {
      0.assertType(CONFLICT, BOTH)
      0.assertContent("", 1, 1)
      0.assertResolved(NONE)
    }
  }

  fun testModifications() {
    test1("x", "x", "y") {
      0.apply(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("y")
    }

    test1("x", "x", "y") {
      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }

    test1("X_x_Z", "X_x_Z", "X_y_Z") {
      0.apply(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("X_y_Z")
    }

    test1("z", "x", "y") {
      0.apply(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("y")

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("y_z")

      assertContent("y_z")
    }

    test1("z", "x", "y") {
      0.apply(Side.LEFT)
      0.assertResolved(LEFT)
      0.assertContent("z")

      0.apply(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("z_y")

      assertContent("z_y")
    }

    test1("X", "X", "X_y") {
      0.apply(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("X_y")
    }

    test1("X", "X", "X_y") {
      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("")

      assertContent("X")
    }

    test1("X_z", "X", "X_y") {
      0.apply(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("y")

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("y_z")

      assertContent("X_y_z")
    }

    test1("X_z", "X_y", "X") {
      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("z")

      assertContent("X_z")
    }

    test1("X_z", "X_y", "X") {
      0.apply(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("")

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("z")

      assertContent("X_z")
    }
  }

  fun testModificationsIgnore() {
    test1("x", "x", "y") {
      0.ignore(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }

    test1("x", "x", "y") {
      0.ignore(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }

    test1("z", "x", "y") {
      0.ignore(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("x")

      0.ignore(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }

    test1("z", "x", "y") {
      0.apply(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("y")

      0.ignore(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("y")
    }

    test1("z", "x", "y") {
      0.ignore(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("x")

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("z")

      assertContent("z")
    }

    test1("X_z", "X_y", "X") {
      0.ignore(Side.RIGHT)
      0.assertResolved(RIGHT)
      0.assertContent("y")

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertContent("z")

      assertContent("X_z")
    }

    test1("X_z", "X_y", "X") {
      0.ignore(Side.LEFT)
      0.assertResolved(LEFT)
      0.assertContent("y")

      0.apply(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertContent("")

      assertContent("X")
    }
  }

  fun testModificationsModifiers() {
    test1("x", "x", "y") {
      0.apply(Side.RIGHT, true)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("y")
    }

    test1("x", "x", "y") {
      0.ignore(Side.RIGHT, true)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }

    test1("z", "x", "y") {
      0.apply(Side.RIGHT, true)
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("y")
    }

    test1("z", "x", "y") {
      0.ignore(Side.RIGHT, true)
      0.assertResolved(BOTH)
      0.assertContent("x")

      assertContent("x")
    }
  }

  fun testResolve() {
    test1("y z", "x y z", "x y") {
      0.resolve()
      0.assertResolved(BOTH)
      0.assertContent("y")

      assertContent("y")
    }

    test2("y z_Y_x y", "x y z_Y_x y z", "x y_Y_y z") {
      0.resolve()
      0.assertResolved(BOTH)
      0.assertContent("y")

      1.resolve()
      1.assertResolved(BOTH)
      1.assertContent("y")

      assertContent("y_Y_y")
    }

    test2("y z_Y_x y", "x y z_Y_x y z", "x y_Y_y z") {
      0.apply(Side.LEFT, true)
      0.assertResolved(BOTH)
      0.assertContent("y z")

      1.resolve()
      1.assertResolved(BOTH)
      1.assertContent("y")

      assertContent("y z_Y_y")
    }

    test1("y z", "x y z", "x y") {
      assertTrue(0.canResolveConflict())

      replaceText(2, 3, "U")
      assertContent("x U z")

      assertFalse(0.canResolveConflict())
    }

    test2("y z_Y_x y", "x y z_Y_x y z", "x y_Y_y z") {
      assertTrue(0.canResolveConflict())
      assertTrue(1.canResolveConflict())


      replaceText(2, 3, "U")

      assertFalse(0.canResolveConflict())
      assertTrue(1.canResolveConflict())
      assertContent("x U z_Y_x y z")


      checkUndo(1) {
        1.resolve()
      }
      1.assertResolved(BOTH)
      1.assertContent("y")

      assertFalse(0.canResolveConflict())
      assertFalse(1.canResolveConflict())
      assertContent("x U z_Y_y")


      replaceText(2, 3, "y")

      assertTrue(0.canResolveConflict())
      assertFalse(1.canResolveConflict())


      checkUndo(1) {
        0.resolve()
      }

      assertFalse(0.canResolveConflict())
      assertFalse(1.canResolveConflict())
      assertContent("y_Y_y")
    }
  }

  fun testUndoSimple() {
    test1("x", "y", "z") {
      checkUndo(1) {
        0.apply(Side.RIGHT)
      }
    }

    test1("x", "y", "z") {
      checkUndo(1) {
        0.apply(Side.RIGHT, true)
      }
    }

    test1("x", "y", "z") {
      checkUndo(1) {
        0.ignore(Side.RIGHT)
      }
    }

    test1("x", "y", "z") {
      checkUndo(1) {
        0.ignore(Side.RIGHT, true)
      }
    }

    test1("x", "y", "z") {
      checkUndo(2) {
        0.apply(Side.RIGHT)
        0.apply(Side.LEFT)
      }
    }

    test1("x", "y", "z") {
      checkUndo(2) {
        0.apply(Side.RIGHT)
        0.apply(Side.RIGHT)
      }
    }

    test1("X", "X_y", "X") {
      checkUndo(1) {
        0.apply(Side.RIGHT)
      }
    }

    test1("X", "X", "X_y") {
      checkUndo(1) {
        0.apply(Side.LEFT)
      }
    }

    test1("X", "X", "X_y") {
      checkUndo(1) {
        0.apply(Side.RIGHT)
      }
    }

    test1("X_z", "X_y", "X") {
      checkUndo(1) {
        0.apply(Side.LEFT)
      }
    }

    test1("X_z", "X_y", "X") {
      checkUndo(1) {
        0.apply(Side.RIGHT)
      }
    }

    test2("y_X", "X", "X_z") {
      checkUndo(1) {
        0.apply(Side.LEFT)
      }
      checkUndo(1) {
        1.apply(Side.RIGHT)
      }

      assertContent("y_X_z")
    }
  }

  fun testRangeModification() {
    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { replaceText(!2 - 0, !2 - 1, "D") }
      0.assertContent("a_D_c", 1, 4)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { replaceText(!2 - -1, !2 - 2, "D") }
      0.assertContent("aDc", 1, 2)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { replaceText("c", "u_x") }
      0.assertContent("a_b_u_x", 1, 5)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { deleteText("c_") }
      0.assertContent("a_b", 1, 3)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { insertTextBefore("b", "q") }
      0.assertContent("a_qb_c", 1, 4)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { insertTextAfter("b", "q") }
      0.assertContent("a_bq_c", 1, 4)
    }

    test1("X_x_y_z_Y", "X_a_b_c_Y", "X_x_y_z_Y") {
      0.assertContent("a_b_c", 1, 4)
      checkUndo(1) { insertText(0, "a_b_c_") }
      0.assertContent("a_b_c", 4, 7)
    }

    test1("A_X_x_y_z_Y", "A_X_a_b_c_Y", "A_X_x_y_z_Y") {
      0.assertContent("a_b_c", 2, 5)
      checkUndo(1) { replaceText("X_a_b", "q_w_e") }
      0.assertContent("c")
    }

    test1("X_x_y_z_Y_A", "X_a_b_c_Y_A", "X_x_y_z_Y_A") {
      0.assertContent("a_b_c")
      checkUndo(1) { replaceText("c_Y", "q") }
      0.assertContent("a_b")
    }

    test1("A_X_x_Y_A", "A_X_b_Y_A", "A_X_x_z_Y_A") {
      0.assertContent("b")
      checkUndo(1) { replaceText("X_b_Y", "q") }

      0.assertContent("", 2, 2)
      assertContent("A_q_A")
    }
  }

  fun testNonConflictsActions() {
    val text1 = """
                1 ======
                insert left
                2 ======
                remove right
                3 ======
                new both
                4 ======
                modify both
                5 ======
                modify
                6 ======
                7 ======
                8 ======""".trimIndent()
    val text2 = """
                1 ======
                2 ======
                remove right
                3 ======
                4 ======
                modify
                5 ======
                modify
                6 ======
                7 ======
                delete modify
                8 ======""".trimIndent()
    val text3 = """
                1 ======
                2 ======
                3 ======
                new both
                4 ======
                modify both
                5 ======
                modify right
                6 ======
                7 ======
                modify
                8 ======""".trimIndent()

    testN(text1, text2, text3) {
      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.BASE)
      }

      assertChangesCount(1)
      assertContent("""
                    1 ======
                    insert left
                    2 ======
                    3 ======
                    new both
                    4 ======
                    modify both
                    5 ======
                    modify right
                    6 ======
                    7 ======
                    delete modify
                    8 ======""".trimIndent())
    }

    testN(text1, text2, text3) {
      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.LEFT)
      }

      assertChangesCount(3)
      assertContent("""
                    1 ======
                    insert left
                    2 ======
                    remove right
                    3 ======
                    new both
                    4 ======
                    modify both
                    5 ======
                    modify
                    6 ======
                    7 ======
                    delete modify
                    8 ======""".trimIndent())
    }

    testN(text1, text2, text3) {
      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.RIGHT)
      }

      assertChangesCount(2)
      assertContent("""
                    1 ======
                    2 ======
                    3 ======
                    new both
                    4 ======
                    modify both
                    5 ======
                    modify right
                    6 ======
                    7 ======
                    delete modify
                    8 ======""".trimIndent())
    }

    testN(text1, text2, text3) {
      replaceText(!5 - 0, !5 - 0, "USER ")

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.BASE)
      }

      assertChangesCount(2)
      assertContent("""
                    1 ======
                    insert left
                    2 ======
                    3 ======
                    new both
                    4 ======
                    USER modify
                    5 ======
                    modify right
                    6 ======
                    7 ======
                    delete modify
                    8 ======""".trimIndent())
    }

    testN(text1, text2, text3) {
      replaceText(!7 - 0, !7 - 0, "USER ")

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.RIGHT)
      }

      assertChangesCount(3)
      assertContent("""
                    1 ======
                    2 ======
                    3 ======
                    new both
                    4 ======
                    modify both
                    5 ======
                    USER modify
                    6 ======
                    7 ======
                    delete modify
                    8 ======""".trimIndent())
    }
  }
}
