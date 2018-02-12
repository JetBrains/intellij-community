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

class WordComparisonUtilTest : ComparisonUtilTestBase() {
  fun testSimpleCases() {
    lines_inner {
      ("x z" - "y z")
      ("-  " - "-  ").default()
      testAll()
    }

    lines_inner {
      ("x z" - "y z")
      ("-  " - "-  ").default()
      testAll()
    }

    lines_inner {
      (" x z" - "y z")
      ("--  " - "-  ").default()
      (" -  " - "-  ").trim()
      testAll()
    }

    lines_inner {
      ("x z " - "y z")
      ("-  -" - "-  ").default()
      ("-   " - "-  ").trim()
      testAll()
    }

    lines_inner {
      ("x z " - "y z")
      ("-  -" - "-  ").default()
      ("-   " - "-  ").trim()
      testAll()
    }

    lines_inner {
      ("x z" - " y z ")
      ("-  " - "--  -").default()
      ("-  " - " -   ").trim()
      testAll()
    }

    lines_inner {
      ("x y" - "x z ")
      ("  -" - "  --").default()
      ("  -" - "  - ").trim()
      testAll()
    }

    lines_inner {
      ("x,y" - "x")
      (" --" - " ").default()
      testAll()
    }

    lines_inner {
      ("x,y" - "y")
      ("-- " - " ").default()
      testAll()
    }

    lines_inner {
      (".x=" - ".!=")
      (" - " - " - ").default()
      testAll()
    }

    lines_inner {
      ("X xyz1 Z" - "X xyz2 Z")
      ("  ----  " - "  ----  ").default()
      testAll()
    }
  }

  fun testPunctuation() {
    lines_inner {
      (" x.z.x " - "x..x")
      ("-  -  -" - "    ").default()
      ("   -   " - "    ").trim()
      testAll()
    }

    lines_inner {
      ("x..x" - " x.z.x ")
      ("    " - "-  -  -").default()
      ("    " - "   -   ").trim()
      testAll()
    }

    lines_inner {
      ("x ... z" - "y ... z")
      ("-      " - "-      ").default()
      testAll()
    }

    lines_inner {
      ("x ... z" - "x ... y")
      ("      -" - "      -").default()
      testAll()
    }

    lines_inner {
      ("x ,... z" - "x ... y")
      ("  -    -" - "      -").default()
      testAll()
    }

    lines_inner {
      ("x . , .. z" - "x ... y")
      ("   ---   -" - "      -").default()
      ("    -    -" - "      -").ignore()
      testAll()
    }

    lines_inner {
      ("x==y==z" - "x====z")
      ("   -   " - "      ").default()
      testAll()
    }

    lines_inner {
      ("x====z" - "x==t==z")
      ("      " - "   -   ").default()
      testAll()
    }

    lines_inner {
      ("X Y ) {_ A B C" - "X Y Z ) {_ y B C ) {")
      ("         -    " - "   --      -    ----").default()
      ("         -    " - "    -      -     ---").ignore()
      testAll()
    }

    // TODO
    words {
      ("@Deprecated @NotNull" - "@NotNull")
      (" ------------       " - "        ").default()
      testAll()
    }

    // TODO
    words {
      ("@Deprecated_ @NotNull" - "@NotNull")
      (" -------------       " - "        ").default()
      testAll()
    }
  }

  fun testOldDiffBug() {
    lines_inner {
      ("x'y'>" - "x'>")
      ("  -- " - "   ").default()
      testAll()
    }

    lines_inner {
      ("x'>" - "x'y'>")
      ("   " - "  -- ").default()
      testAll()
    }

    lines_inner {
      ("x'>" - "x'y'>")
      ("   " - "  -- ").default()
      testAll()
    }

    lines_inner {
      ("x'y'>" - "x'>")
      ("  -- " - "   ").default()
      testAll()
    }
  }

  fun testWhitespaceOnlyChanges() {
    lines_inner {
      ("x  =z" - "x=  z")
      (" --  " - "  -- ").default()
      testDefault()
      testTrim()
    }

    lines_inner {
      ("x  =" - "x=  z")
      (" -- " - "  ---").default()
      ("    " - "    -").ignore()
      testAll()
    }
  }

  fun testNewlines() {
    lines_inner {
      (" x _ y _ z " - "x z")
      ("- ------  -" - "   ").default()
      ("     -     " - "   ").trim()
      ("     -     " - "   ").ignore()
      testAll()
    }

    lines_inner {
      ("x z" - " x _ y _ z ")
      ("   " - "- ------  -").default()
      ("   " - "     -     ").trim()
      ("   " - "     -     ").ignore()
      testAll()
    }

    words {
      ("_i" - "i_")
      ("- " - " -").default()
      ("  " - "  ").trim()
      testAll()
    }

    words {
      ("i_" - "_i")
      ("- " - " -").default() // TODO
      testAll()
    }

    words {
      ("x_y" - "xy")
      ("   " - "  ").ignore()
      testIgnore()
    }

    words {
      ("A x_y B" - "a xy b")
      ("-------" - "------").ignore()
      testIgnore()
    }

    words {
      ("A xy B" - "a xy b")
      ("-    -" - "-    -").ignore()
      testIgnore()
    }

    words {
      ("A_B_" - "X_")
      ("--- " - "- ").default()
      testAll()
    }
  }

  fun testFixedBugs() {
    lines_inner {
      (".! " - ".  y!")
      ("  -" - " --- ").default()
      ("   " - " --- ").trim()
      ("   " - "   - ").ignore()
      testAll()
    }

    lines_inner {
      (" x n" - " y_  x m")
      ("   -" - "----   -").default()
      ("   -" - " -     -").trim()
      ("   -" - " -     -").ignore()
      testAll()
    }

    lines_inner {
      ("x_" - "x!  ")
      (" -" - " ---").default()
      ("  " - " -  ").trim()
      ("  " - " -  ").ignore()
      testAll()
    }
  }

  fun testInnerWhitespaces() {
    lines_inner {
      ("<< x >>" - "<.<>.>")
      ("  ---  " - " -  - ").default()
      ("   -   " - " -  - ").ignore()
      testAll()
    }

    lines_inner {
      ("<< x >>" - "y<<x>>y")
      ("  - -  " - "-     -").default()
      ("       " - "-     -").ignore()
      testAll()
    }

    lines_inner {
      ("x .. z" - "x y .. z")
      ("      " - " --     ").default()
      ("      " - "  -     ").ignore()
      testAll()
    }

    lines_inner {
      ("  x..z" - "x..y  ")
      ("--   -" - "   ---").default()
      ("     -" - "   -  ").trim()
      testAll()
    }

    lines_inner {
      (" x y x _ x z x " - "x x_x x")
      ("- --  - - --  -" - "       ").default()
      ("  --      --   " - "       ").trim()
      ("   -       -   " - "       ").ignore()
      testAll()
    }
  }

  fun testAlgorithmSpecific() {
    // prefer words over punctuation
    lines_inner {
      ("...x" - "x...")
      ("--- " - " ---").default()
      testAll()
    }

    // prefer longer words sequences
    lines_inner {
      ("x x y" - "x y")
      ("--   " - "   ").default()
      ("-    " - "   ").ignore()
      testAll()
    }

    lines_inner {
      ("y x x" - "y x")
      ("   --" - "   ").default()
      ("    -" - "   ").ignore()
      testAll()
    }

    lines_inner {
      ("A X A B" - "A B")
      ("----   " - "   ").default()
      ("---    " - "   ").ignore()
      testAll()
    }

    // prefer less modified 'sentences'
    lines_inner {
      ("A.X A.Z" - "A.X A.Y A.Z")
      ("       " - "   ----    ").default()
      ("       " - "    ---    ").ignore()
      testAll()
    }

    lines_inner {
      ("X.A Z.A" - "X.A Y.A Z.A")
      ("       " - "   ----    ").default()
      ("       " - "    ---    ").ignore()
      testAll()
    }

    // prefer punctuation over whitespaces
    lines_inner {
      (".   " - "   .")
      (" ---" - "--- ").default()
      testDefault()
    }

    lines_inner {
      ("A B_C D" - "A_B C_D")
      (" -  -- " - " - --  ").default()
      (" -  -- " - "   --  ").trim()
      ("    -  " - "    -  ").ignore()
      testAll()
    }

    lines_inner {
      ("B_C_D_" - "X_Y_Z_")
      ("- - - " - "- - - ").default()
      testAll()
    }

    words {
      ("!x_!_z" - "!_!_y z")
      (" -    " - "    -- ").default()
      testDefault()
    }
  }

  fun `test trailing punctuation`() {
    lines_inner {
      ("X = { };" - "X = { _ };")
      ("        " - "     --   ").default()
      ("        " - "          ").trim()
      testAll()
    }

    // TODO
    lines_inner {
      ("X = { };_" - "X = { _ };_")
      ("      -- " - "       ----").default()
      ("      -- " - "        -- ").trim()
      testAll()
    }
  }

  fun `test legacy cases from ByWordTest`() {
    lines_inner {
      ("abc def, 123" - "ab def, 12")
      ("---      ---" - "--      --").default()
      testAll()
    }

    lines_inner {
      (" a[xy]+1" - ",a[]+1")
      ("-  --   " - "-     ").default()
      ("   --   " - "-     ").trim()
      testAll()
    }

    lines_inner {
      ("0987_  a.g();_" - "yyyy_")
      ("------------- " - "---- ").default()
      testAll()
    }

    lines_inner {
      ("  abc_2222_" - "    x = abc_zzzz_")
    //("      ---- " - "--  ----    ---- ").legacy()
      ("      ---- " - " ------     ---- ").default()
      ("      ---- " - "    ---     ---- ").trim()
      testAll()
    }

    lines_inner { // Idea58505
      ("   if (eventMerger!=null && !dataSelection.getValueIsAdjusting()) {" -
       "   if (eventMerger!=null && (dataSelection==null || !dataSelection.getValueIsAdjusting())) {")
    //("                            -                                      " -
    // "                            -             ------------------------                       -  ").legacy()
      ("                                                                   " -
       "                           ------------------------                                      -  ").default()
      ("                                                                   " -
       "                            -----------------------                                      -  ").ignore()
      testAll()
    }

    lines_inner { // Idea56428
      ("messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?)\");" -
       "messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, CREATION_TIMESTAMP, STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?, ?)\");").plainSource()
    //("                                                     .                                                                                                     .   " -
    // "                                                     .                                   --------------------                                                                 --- .   ").legacy()
      ("                                                     .                                                                                                     .   " -
       "                                                     .                                  --------------------                                                                  --- .   ").default()
      ("                                                     .                                                                                                     .   " -
       "                                                     .                                   -------------------                                                                  --- .   ").ignore()
      testAll()
    }

    lines_inner {
      ("f(a, b);" - "f(a,_  b);")
      ("        " - "    --    ").default()
      ("        " - "          ").trim()
      testAll()
    }

    lines_inner {
      (" o.f(a)" - "o. f( b)")
      ("-    - " - "  -  -- ").default()
      ("     - " - "  -  -- ").trim()
      ("     - " - "      - ").ignore()
      testAll()
    }

    lines_inner {
      (" 123 " - "xyz")
      (" --- " - "---").trim()
      testTrim()
    }
  }

  fun testEmptyRangePositions() {
    lines_inner {
      ("x? y" - "x y")
      (" -  " - "   ").default()
      default(del(1, 1, 1))
      testAll()
    }

    lines_inner {
      ("x ?y" - "x y")
      ("  - " - "   ").default()
      default(del(2, 2, 1))
      testAll()
    }
  }
}