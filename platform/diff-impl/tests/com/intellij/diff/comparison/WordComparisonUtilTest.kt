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
    words {
      ("x z" - "y z")
      ("-  " - "-  ").default()
      testAll()
    }

    words {
      ("x z" - "y z")
      ("-  " - "-  ").default()
      testAll()
    }

    words {
      (" x z" - "y z")
      ("--  " - "-  ").default()
      (" -  " - "-  ").trim()
      testAll()
    }

    words {
      ("x z " - "y z")
      ("-  -" - "-  ").default()
      ("-   " - "-  ").trim()
      testAll()
    }

    words {
      ("x z " - "y z")
      ("-  -" - "-  ").default()
      ("-   " - "-  ").trim()
      testAll()
    }

    words {
      ("x z" - " y z ")
      ("-  " - "--  -").default()
      ("-  " - " -   ").trim()
      testAll()
    }

    words {
      ("x y" - "x z ")
      ("  -" - "  --").default()
      ("  -" - "  - ").trim()
      testAll()
    }

    words {
      ("x,y" - "x")
      (" --" - " ").default()
      testAll()
    }

    words {
      ("x,y" - "y")
      ("-- " - " ").default()
      testAll()
    }

    words {
      (".x=" - ".!=")
      (" - " - " - ").default()
      testAll()
    }
  }

  fun testPunctuation() {
    words {
      (" x.z.x " - "x..x")
      ("-  -  -" - "    ").default()
      ("   -   " - "    ").trim()
      testAll()
    }

    words {
      ("x..x" - " x.z.x ")
      ("    " - "-  -  -").default()
      ("    " - "   -   ").trim()
      testAll()
    }

    words {
      ("x ... z" - "y ... z")
      ("-      " - "-      ").default()
      testAll()
    }

    words {
      ("x ... z" - "x ... y")
      ("      -" - "      -").default()
      testAll()
    }

    words {
      ("x ,... z" - "x ... y")
      ("  -    -" - "      -").default()
      testAll()
    }

    words {
      ("x . , .. z" - "x ... y")
      ("   ---   -" - "      -").default()
      ("    -    -" - "      -").ignore()
      testAll()
    }

    words {
      ("x==y==z" - "x====z")
      ("   -   " - "      ").default()
      testAll()
    }

    words {
      ("x====z" - "x==t==z")
      ("      " - "   -   ").default()
      testAll()
    }
  }

  fun testOldDiffBug() {
    words {
      ("x'y'>" - "x'>")
      ("  -- " - "   ").default()
      testAll()
    }

    words {
      ("x'>" - "x'y'>")
      ("   " - "  -- ").default()
      testAll()
    }
  }

  fun testWhitespaceOnlyChanges() {
    words {
      ("x  =z" - "x=  z")
      (" --  " - "  -- ").default()
      testDefault()
      testTrim()
    }

    words {
      ("x  =" - "x=  z")
      (" -- " - "  ---").default()
      ("    " - "    -").ignore()
      testAll()
    }
  }

  fun testNewlines() {
    words {
      (" x _ y _ z " - "x z")
      ("- ------  -" - "   ").default()
      ("     -     " - "   ").trim()
      ("     -     " - "   ").ignore()
      testAll()
    }

    words {
      ("x z" - " x _ y _ z ")
      ("   " - "- ------  -").default()
      ("   " - "     -     ").trim()
      ("   " - "     -     ").ignore()
      testAll()
    }
  }

  fun testFixedBugs() {
    words {
      (".! " - ".  y!")
      ("  -" - " --- ").default()
      ("   " - " --- ").trim()
      ("   " - "   - ").ignore()
      testAll()
    }

    words {
      (" x n" - " y_  x m")
      ("   -" - "----   -").default()
      ("   -" - " -     -").trim()
      ("   -" - " -     -").ignore()
      testAll()
    }

    words {
      ("x_" - "x!  ")
      (" -" - " ---").default()
      ("  " - " -  ").trim()
      ("  " - " -  ").ignore()
      testAll()
    }
  }

  fun testInnerWhitespaces() {
    words {
      ("<< x >>" - "<.<>.>")
      ("  ---  " - " -  - ").default()
      ("   -   " - " -  - ").ignore()
      testAll()
    }

    words {
      ("<< x >>" - "y<<x>>y")
      ("  - -  " - "-     -").default()
      ("       " - "-     -").ignore()
      testAll()
    }

    words {
      ("x .. z" - "x y .. z")
      ("      " - " --     ").default() // TODO: looks wrong
      ("      " - "  -     ").ignore()
      testAll()
    }

    words {
      ("  x..z" - "x..y  ")
      ("--   -" - "   ---").default()
      ("     -" - "   -  ").trim()
      testAll()
    }

    words {
      (" x y x _ x z x " - "x x_x x")
      ("- --  - - --  -" - "       ").default()
      ("  --      --   " - "       ").trim()
      ("   -       -   " - "       ").ignore()
      testAll()
    }
  }

  fun testAlgorithmSpecific() {
    // prefer words over punctuation
    words {
      ("...x" - "x...")
      ("--- " - " ---").default()
      testAll()
    }

    // prefer longer words sequences
    words {
      ("x x y" - "x y")
      ("--   " - "   ").default()
      ("-    " - "   ").ignore()
      testAll()
    }

    words {
      ("y x x" - "y x")
      ("   --" - "   ").default()
      ("    -" - "   ").ignore()
      testAll()
    }

    words {
      ("A X A B" - "A B")
      ("----   " - "   ").default()
      ("---    " - "   ").ignore()
      testAll()
    }

    // prefer less modified 'sentences'
    words {
      ("A.X A.Z" - "A.X A.Y A.Z")
      ("       " - "   ----    ").default()
      ("       " - "    ---    ").ignore()
      testAll()
    }

    words {
      ("X.A Z.A" - "X.A Y.A Z.A")
      ("       " - "   ----    ").default()
      ("       " - "    ---    ").ignore()
      testAll()
    }

    // prefer punctuation over whitespaces
    words {
      (".   " - "   .")
      (" ---" - "--- ").default()
      testDefault()
    }
  }

  fun `test legacy cases from ByWordTest`() {
    words {
      ("abc def, 123" - "ab def, 12")
      ("---      ---" - "--      --").default()
      testAll()
    }

    words {
      (" a[xy]+1" - ",a[]+1")
      ("-  --   " - "-     ").default()
      ("   --   " - "-     ").trim()
      testAll()
    }

    words {
      ("0987_  a.g();_" - "yyyy_")
      ("------------- " - "---- ").default()
      testAll()
    }

    words {
      ("  abc_2222_" - "    x = abc_zzzz_")
    //("      ---- " - "--  ----    ---- ").legacy()
      ("      ---- " - " ------     ---- ").default()
      ("      ---- " - "    ---     ---- ").trim()
      testAll()
    }

    words { // Idea58505
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

    words { // Idea56428
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

    words {
      ("f(a, b);" - "f(a,_  b);")
      ("        " - "    --    ").default()
      ("        " - "          ").trim()
      testAll()
    }

    words {
      (" o.f(a)" - "o. f( b)")
      ("-    - " - "  -  -- ").default()
      ("     - " - "  -  -- ").trim()
      ("     - " - "      - ").ignore()
      testAll()
    }

    words {
      (" 123 " - "xyz")
      (" --- " - "---").trim()
      testTrim()
    }
  }
}