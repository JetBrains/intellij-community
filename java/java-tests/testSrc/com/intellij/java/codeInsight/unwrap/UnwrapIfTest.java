// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapIfTest extends UnwrapTestCase {
  public void testDoesNothingOutsideOfStatements() {
    assertUnwrapped("<caret>int i = 0;\n",

                    "<caret>int i = 0;\n");
  }

  public void testIfWithSingleStatement() {
    assertUnwrapped("if(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testIfBlock() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfBlockWithComment() {
    assertUnwrapped("if(true) {\n" +
                    "    // a comment\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "// a comment\n" +
                    "int i;<caret>\n");
  }

  public void testIfMultiStatementBlock() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;\n" +
                    "\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;\n" +
                    "\n" +
                    "int j;<caret>\n");
  }

  public void testIfEmpty() {
    assertUnwrapped("{\n" +
                    "    if(true<caret>);\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testIfEmptyBlock() {
    assertUnwrapped("{\n" +
                    "    if(true<caret>) {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testIfIncomplete() {
    assertUnwrapped("{\n" +
                    "    if(true<caret>)\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testDoesNotAffectNeighbours() {
    assertUnwrapped("if(true) {}\n" +
                    "if(false) {<caret>}\n" +
                    "if(true && false) {}\n",

                    "if(true) {}\n" +
                    "<caret>if(true && false) {}\n");
  }

  public void testIfWithElse() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else {\n" +
                    "    int j;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfWithElses() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else if (true) {\n" +
                    "    int j;\n" +
                    "} else {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfElseIncomplete() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else \n",

                    "int i;<caret>\n");
  }

  public void testUnwrapElse() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else Sys<caret>tem.gc();\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testUnwrapElseBlock() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else {\n" +
                    "        int j;<caret>\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    int j;<caret>\n" +
                    "}\n");
  }

  public void testUnwrapElseIf() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else if (false) {\n" +
                    "        int j;<caret>\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    int j;<caret>\n" +
                    "}\n");
  }

  public void testUnwrapIfElseIfElse() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else if (false) {\n" +
                    "        int j;\n" +
                    "    } else {\n" +
                    "        int k;<caret>\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    int k;<caret>\n" +
                    "}\n");
  }

  public void testUnwrapElseWhenCaretRightInTheElseKeyword() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } el<caret>se {\n" +
                    "        int j;\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    int j;<caret>\n" +
                    "}\n");
  }

  public void testRemovesWhileIfWhenElseIsIsIncomplete() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } el<caret>se \n" +
                    "}\n",

                    "{\n" +
                    "    int i;<caret>\n" +
                    "}\n");
  }

  public void testRemoveElse() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else {\n" +
                    "        int j;<caret>\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    }<caret>\n" +
                    "}\n",

                    1);
  }

  public void testRemoveElseWhenCaretRightInTheElseKeyword() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } el<caret>se {\n" +
                    "        int j;\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    }<caret>\n" +
                    "}\n",

                    1);
  }

  public void testRemoveElseIf() {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else if(true) {\n" +
                    "        int j;<caret>\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    }<caret>\n" +
                    "}\n",

                    1);
  }

  public void testRemoveElseIfElse() {
    assertUnwrapped("if(true) {\n" +
                    "    int i;\n" +
                    "} else if (true) {\n" +
                    "    int j;<caret>\n" +
                    "} else {\n" +
                    "    int k;\n" +
                    "}\n",

                    "if(true) {\n" +
                    "    int i;\n" +
                    "}<caret> else {\n" +
                    "    int k;\n" +
                    "}\n",

                    1);
  }

  public void testIfOption() {
    assertOptions("if (true) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'if...'");
  }

  public void testIfElseOption() {
    assertOptions("if (true) {\n" +
                  "} else {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testDoesNotIncludeDirectParentIfsWhenElseIsSelected() {
    assertOptions("if (true) {\n" +
                  "} else if (true) {\n" +
                  "} else if (true) {\n" +
                  "} else {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testDoesNotIncludeIndirectParentIfsWhenElseIsSelected() {
    assertOptions("if (true) {\n" +
                  "} else if (true) {\n" +
                  "} else {\n" +
                  "    if (true) {\n" +
                  "    } else {\n" +
                  "        <caret>\n" +
                  "    }\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'",
                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testIfElseIfOption() {
    assertOptions("if (true) {\n" +
                  "} else if (false) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testIfInsideElseOption() {
    assertOptions("if (true) {\n" +
                  "} else {\n" +
                  "  if (false) {\n" +
                  "    <caret>\n" +
                  "  }\n" +
                  "}\n",

                  "Unwrap 'if...'",
                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testIfElseOptionWhenCaretIsRughtOnTheElseKeyword() {
    assertOptions("if (true) {\n" +
                  "} el<caret>se {\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }
}