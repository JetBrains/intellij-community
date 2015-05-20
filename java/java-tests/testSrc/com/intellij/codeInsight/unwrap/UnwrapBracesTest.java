package com.intellij.codeInsight.unwrap;

public class UnwrapBracesTest extends UnwrapTestCase {
  public void testBraces() throws Exception {
    assertUnwrapped("{\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testEmptyBraces() throws Exception {
    assertUnwrapped("{\n" +
                    "    {<caret>}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testBracesWithComments() throws Exception {
    assertUnwrapped("{\n" +
                    "    // a <caret>comment\n" +
                    "    int i = 0;\n" +
                    "}\n",

                    "// a <caret>comment\n" +
                    "int i = 0;\n");
  }

  public void testTrimmingTheLeadingAndTrailingWhileSpaces() throws Exception {
    assertUnwrapped("{\n" +
                    "    \n" +
                    "    \n" +
                    "    int i<caret> = 0;\n" +
                    "    \n" +
                    "    \n" +
                    "}\n",

                    "int i<caret> = 0;\n");
  }

  public void testBracesOptions() throws Exception {
    assertOptions("{\n" +
                  "    {\n" +
                  "        int i;<caret>\n" +
                  "    }\n" +
                  "}\n",

                  "Unwrap braces",
                  "Unwrap braces");
  }
}
