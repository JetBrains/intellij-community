package com.intellij.codeInsight.unwrap;

public class UnwrapIfTest extends UnwrapTestCase {
  public void testDoesNothingOutsideOfStatements() throws Exception {
    assertUnwrapped("<caret>int i = 0;\n",

                    "<caret>int i = 0;\n");
  }

  public void testIfWithSingleStatement() throws Exception {
    assertUnwrapped("if(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testIfBlock() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfBlockWithComment() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    // a comment\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "// a comment\n" +
                    "int i;<caret>\n");
  }

  public void testIfMultiStatementBlock() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    int i;\n" +
                    "\n" +
                    "    int j;<caret>\n" +
                    "}\n",

                    "int i;\n" +
                    "\n" +
                    "int j;<caret>\n");
  }

  public void testIfEmpty()throws Exception {
    assertUnwrapped("{\n" +
                    "    if(true<caret>);\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testIfEmptyBlock() throws Exception {
    assertUnwrapped("{\n" +
                    "    if(true<caret>) {}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testIfIncomplete() throws Exception {
    assertUnwrapped("{\n" +
                    "    if(true<caret>)\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testDoesNotAffectNeighbours() throws Exception {
    assertUnwrapped("if(true) {}\n" +
                    "if(false) {<caret>}\n" +
                    "if(true && false) {}\n",

                    "if(true) {}\n" +
                    "<caret>if(true && false) {}\n");
  }

  public void testIfWithElse() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else {\n" +
                    "    int j;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfWithElses() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else if (true) {\n" +
                    "    int j;\n" +
                    "} else {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testIfElseIncomplete() throws Exception {
    assertUnwrapped("if(true) {\n" +
                    "    int i;<caret>\n" +
                    "} else \n",

                    "int i;<caret>\n");
  }

  public void testUnwrapElse() throws Exception {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } else Sys<caret>tem.gc();\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testUnwrapElseBlock() throws Exception {
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

  public void testUnwrapElseIf() throws Exception {
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

  public void testUnwrapIfElseIfElse() throws Exception {
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

  public void testUnwrapElseWhenCaretRightInTheElseKeyword() throws Exception {
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

  public void testRemovesWhileIfWhenElseIsIsIncomplete() throws Exception {
    assertUnwrapped("{\n" +
                    "    if(true) {\n" +
                    "        int i;\n" +
                    "    } el<caret>se \n" +
                    "}\n",

                    "{\n" +
                    "    int i;<caret>\n" +
                    "}\n");
  }

  public void testRemoveElse() throws Exception {
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

  public void testRemoveElseWhenCaretRightInTheElseKeyword() throws Exception {
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

  public void testRemoveElseIf() throws Exception {
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

  public void testRemoveElseIfElse() throws Exception {
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

  public void testIfOption() throws Exception {
    assertOptions("if (true) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'if...'");
  }

  public void testIfElseOption() throws Exception {
    assertOptions("if (true) {\n" +
                  "} else {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testDoesNotIncludeDirectParentIfsWhenElseIsSelected() throws Exception {
    assertOptions("if (true) {\n" +
                  "} else if (true) {\n" +
                  "} else if (true) {\n" +
                  "} else {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testDoesNotIncludeIndirectParentIfsWhenElseIsSelected() throws Exception {
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

  public void testIfElseIfOption() throws Exception {
    assertOptions("if (true) {\n" +
                  "} else if (false) {\n" +
                  "    <caret>\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  public void testIfInsideElseOption() throws Exception {
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

  public void testIfElseOptionWhenCaretIsRughtOnTheElseKeyword() throws Exception {
    assertOptions("if (true) {\n" +
                  "} el<caret>se {\n" +
                  "}\n",

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }
}