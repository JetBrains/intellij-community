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
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType.*
import com.intellij.diff.util.ThreeSide
import com.intellij.util.ui.UIUtil

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


  fun testNoConflicts() {
    test2("a_Ins1_b_ccc", "a_b_ccc", "a_b_dddd") {
      0.assertRange(1, 2, 1, 1, 1, 1)
      1.assertRange(3, 4, 2, 3, 2, 3)

      0.apply(Side.LEFT)
      0.assertRange(1, 2, 1, 2, 1, 1)
      1.assertRange(3, 4, 3, 4, 2, 3)
      assertContent("a_Ins1_b_ccc")

      runApplyNonConflictsAction(ThreeSide.BASE)
      0.assertRange(1, 2, 1, 2, 1, 1)
      1.assertRange(3, 4, 3, 4, 2, 3)
      assertContent("a_Ins1_b_dddd")

      0.assertResolved(BOTH)
      1.assertResolved(BOTH)
    }
  }

  fun testConflictingChange() {
    test("1_2_3_X_a_b_c_Y_Ver1_Ver12_Z",
               "X_a_b_c_Y_" + "Ver12_Ver23_Z",
               "X_" +  "Y_" + "Ver23_Ver3_Z", 3) {
      0.assertType(INSERTED)
      1.assertType(DELETED)
      2.assertType(CONFLICT)

      runApplyNonConflictsAction(ThreeSide.BASE)
      0.assertResolved(BOTH)
      1.assertResolved(BOTH)
      2.assertResolved(NONE)

      assertContent("1_2_3_X_Y_Ver12_Ver23_Z")
    }
  }

  fun testApplyMergeThenUndo() {
    test1("X_b_Y", "X_1_2_3_Y", "X_a_Y") {
      0.assertRange(1, 2, 1, 4, 1, 2)

      0.apply(Side.RIGHT)
      0.assertRange(1, 2, 1, 2, 1, 2)
      0.assertContent("a")

      checkUndo(1) {
        0.apply(Side.LEFT)
        0.assertRange(1, 2, 1, 3, 1, 2)
        0.assertContent("a_b")
      }

      assertContent("X_a_b_Y")
    }
  }

  fun testApplyModifiedDeletedConflict() {
    test1("X_Y", "X_1_2_3_Y", "X_a_Y") {
      0.assertRange(1, 1, 1, 4, 1, 2)
      0.assertContent("1_2_3")

      runApplyNonConflictsAction(ThreeSide.BASE)
      0.assertResolved(NONE)

      0.apply(Side.RIGHT)
      0.assertRange(1, 1, 1, 2, 1, 2)
      0.assertResolved(BOTH)

      assertContent("X_a_Y")
    }
  }

  fun testInvalidatingChange() {
    test1("X_1_2_Y", "X_1_Ins_2_Y", "X_1_2_Y") {
      0.assertType(DELETED)
      0.assertRange(2, 2, 2, 3, 2, 2)

      deleteText("1_Ins_2_")
      0.assertResolved(BOTH)
      0.assertRange(2, 2, 2, 2, 2, 2)
    }
  }

  fun testApplySeveralActions() {
    test("X_1_Y_2_Z_3_4_U_W_",
         "X_a_Y_b_Z_c_U_d_W_",
         "X_a_Y_B_Z_C_U_D_W_", 4) {
      0.assertType(MODIFIED)
      1.assertType(CONFLICT)
      2.assertType(CONFLICT)
      3.assertType(CONFLICT)

      0.apply(Side.LEFT)
      0.assertResolved(BOTH)
      0.assertResolved(BOTH)

      3.apply(Side.RIGHT)
      3.assertResolved(BOTH)
      assertContent("X_1_Y_b_Z_c_U_D_W_")

      1.apply(Side.RIGHT)
      1.assertResolved(RIGHT)
      assertContent("X_1_Y_B_Z_c_U_D_W_")

      1.apply(Side.LEFT)
      2.apply(Side.LEFT)
      2.apply(Side.RIGHT)
      1.assertResolved(BOTH)
      2.assertResolved(BOTH)
      assertContent("X_1_Y_B_2_Z_3_4_C_U_D_W_")
    }
  }

  fun testIgnoreChangeAction() {
    test2("X_1_Y_2_Z", "X_a_Y_b_Z", "X_a_Y_B_Z") {
      0.assertType(MODIFIED)
      1.assertType(CONFLICT)

      0.ignore(Side.LEFT)
      0.assertRange(1, 2, 1, 2, 1, 2)
      assertContent("X_a_Y_b_Z")

      0.ignore(Side.RIGHT)
      0.assertResolved(BOTH)
      0.assertRange(1, 2, 1, 2, 1, 2)
      assertContent("X_a_Y_b_Z")

    }
  }

  fun testLongBase() {
    test1("X_1_2_3_Z", "X_1_b_3_d_e_f_Z", "X_a_b_c_Z") {
      0.assertType(CONFLICT)
      0.assertRange(1, 4, 1, 7, 1, 4)
    }
  }

  fun testReplaceBaseWithBranch() {
    test2("a_X_b_c", "A_X_B_c", "1_X_1_c") {
      0.assertType(CONFLICT)
      1.assertType(CONFLICT)

      0.apply(Side.LEFT)
      assertContent("a_X_B_c")

      replaceText("a_X_B_c", "a_X_b_c")

      0.assertRange(0, 1, 0, 1, 0, 1)
      1.assertRange(2, 3, 2, 3, 2, 3)
    }
  }

  fun testError1() {
    testN("start_change_ a_ b",
          "start_CHANGE_   a_   b",
          "    }_    return new DiffFragment(notEmptyContent(buffer1), notEmptyContent(buffer2));_  }") {

    }
  }

  fun testError2() {
    test1("C_X", "C_", "C_") {
      0.assertRange(1, 2, 1, 2, 1, 2)
    }
  }

  fun testError3() {
    testN("original_local_local_local_original_",
          "original_original_original_original_original_",
          "original_remote_remote_remote_original_") {

    }
  }

  fun testIgnored() {
    test(" x_new_y_z", "x_ y _z", "x_y _new_ z", 5, IgnorePolicy.IGNORE_WHITESPACES) {
      0.assertRange(0, 1, 0, 1, 0, 1)
      1.assertRange(1, 2, 1, 1, 1, 1)
      2.assertRange(2, 3, 1, 2, 1, 2)
      3.assertRange(3, 3, 2, 2, 2, 3)
      4.assertRange(3, 4, 2, 3, 3, 4)

      0.assertType(MODIFIED, LEFT)
      1.assertType(INSERTED, LEFT)
      2.assertType(MODIFIED, BOTH)
      3.assertType(INSERTED, RIGHT)
      4.assertType(MODIFIED, RIGHT)

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.BASE)
      }
      assertContent(" x_new_y_new_ z")

      0.assertResolved(BOTH)
      1.assertResolved(BOTH)
      2.assertResolved(BOTH)
      3.assertResolved(BOTH)
      4.assertResolved(BOTH)

      undo(1)

      1.apply(Side.RIGHT)
      2.apply(Side.RIGHT)

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.BASE)
      }

      assertContent(" x_y _new_ z")
    }

    test(" x_new_y1_z", "x_ y _z", "x_y2 _new_ z", 3, IgnorePolicy.IGNORE_WHITESPACES) {
      0.assertRange(0, 1, 0, 1, 0, 1)
      1.assertRange(1, 3, 1, 2, 1, 3)
      2.assertRange(3, 4, 2, 3, 3, 4)

      0.assertType(MODIFIED, LEFT)
      1.assertType(CONFLICT, BOTH)
      2.assertType(MODIFIED, RIGHT)

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.BASE)
      }
      assertContent(" x_ y _ z")

      0.assertResolved(BOTH)
      1.assertResolved(NONE)
      2.assertResolved(BOTH)
    }

    test(" x_new_y_z", "x_ y _z", "x_y _new_ z", 1, IgnorePolicy.DEFAULT) {
      0.assertRange(0, 4, 0, 3, 0, 4)
      0.assertType(CONFLICT, BOTH)

      viewer.textSettings.ignorePolicy = IgnorePolicy.IGNORE_WHITESPACES
      UIUtil.dispatchAllInvocationEvents()
      assertCantUndo()

      0.assertRange(0, 1, 0, 1, 0, 1)
      1.assertRange(1, 2, 1, 1, 1, 1)
      2.assertRange(2, 3, 1, 2, 1, 2)
      3.assertRange(3, 3, 2, 2, 2, 3)
      4.assertRange(3, 4, 2, 3, 3, 4)

      checkUndo(1) {
        runApplyNonConflictsAction(ThreeSide.RIGHT)
      }
      assertContent("x_y _new_ z")

      0.assertResolved(NONE)
      1.assertResolved(NONE)
      2.assertResolved(BOTH)
      3.assertResolved(BOTH)
      4.assertResolved(BOTH)
    }
  }
}
