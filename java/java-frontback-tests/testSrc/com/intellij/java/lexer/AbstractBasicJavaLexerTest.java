// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer;

import com.intellij.testFramework.syntax.LexerTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaLexerTest extends LexerTestCase {
  public void testClassicNumericLiterals() {
    doTest("0 1234 01234 0x1234",
           """
             INTEGER_LITERAL ('0')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('1234')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('01234')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0x1234')""");

    doTest("0L 1234l 01234L 0x1234l",
           """
             LONG_LITERAL ('0L')
             WHITE_SPACE (' ')
             LONG_LITERAL ('1234l')
             WHITE_SPACE (' ')
             LONG_LITERAL ('01234L')
             WHITE_SPACE (' ')
             LONG_LITERAL ('0x1234l')""");

    doTest("0f 1e1f 2.f .3f 0f 3.14f 6.022137e+23f",
           """
             FLOAT_LITERAL ('0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('1e1f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('2.f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('.3f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('3.14f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('6.022137e+23f')""");

    doTest("0d 1e1 2. .3 0.0 3.14 1e-9d 1e137",
           """
             DOUBLE_LITERAL ('0d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e1')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('2.')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('.3')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0.0')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('3.14')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e-9d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e137')""");

    doTest(". e0 x1 ax .e .p 08 .0e .0f",
           """
             DOT ('.')
             WHITE_SPACE (' ')
             IDENTIFIER ('e0')
             WHITE_SPACE (' ')
             IDENTIFIER ('x1')
             WHITE_SPACE (' ')
             IDENTIFIER ('ax')
             WHITE_SPACE (' ')
             DOT ('.')
             IDENTIFIER ('e')
             WHITE_SPACE (' ')
             DOT ('.')
             IDENTIFIER ('p')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('08')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('.0e')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('.0f')""");
  }

  public void testTigerNumericLiterals() {
    doTest("0xap0f 0xab.p0F 0x.abcP0f 0xabc.defP0F",
           """
             FLOAT_LITERAL ('0xap0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0xab.p0F')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0x.abcP0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0xabc.defP0F')""");

    doTest("0xap1 0xab.P12 0x.abcP123d 0xabc.defP1234D",
           """
             DOUBLE_LITERAL ('0xap1')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0xab.P12')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0x.abcP123d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0xabc.defP1234D')""");

    doTest("p0",
           "IDENTIFIER ('p0')");
  }

  public void testCoinNumericLiterals() {
    doTest("1_2 0_1 012__34 0x1_2_3_4 0B0 0b0001_0010_0100_1000",
           """
             INTEGER_LITERAL ('1_2')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0_1')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('012__34')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0x1_2_3_4')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0B0')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0b0001_0010_0100_1000')""");

    doTest("1_2L 0_7l 012__34l 0x1_2_3_4L 0B0L 0b0001_0010_0100_1000l",
           """
             LONG_LITERAL ('1_2L')
             WHITE_SPACE (' ')
             LONG_LITERAL ('0_7l')
             WHITE_SPACE (' ')
             LONG_LITERAL ('012__34l')
             WHITE_SPACE (' ')
             LONG_LITERAL ('0x1_2_3_4L')
             WHITE_SPACE (' ')
             LONG_LITERAL ('0B0L')
             WHITE_SPACE (' ')
             LONG_LITERAL ('0b0001_0010_0100_1000l')""");

    doTest("1_0f 1e1_2f 2_2.f .3_3f 3.14_16f 6.022___137e+2_3f",
           """
             FLOAT_LITERAL ('1_0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('1e1_2f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('2_2.f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('.3_3f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('3.14_16f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('6.022___137e+2_3f')""");

    doTest("0_0d 1e1_1 2_2. .3_3 3.141_592 1e-9_9d 1e1__3_7",
           """
             DOUBLE_LITERAL ('0_0d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e1_1')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('2_2.')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('.3_3')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('3.141_592')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e-9_9d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1e1__3_7')""");

    doTest("0xa_ap1_0f 0xa_b.p22F 0x.ab__cP0f 0xa_bc.d_efP0F",
           """
             FLOAT_LITERAL ('0xa_ap1_0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0xa_b.p22F')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0x.ab__cP0f')
             WHITE_SPACE (' ')
             FLOAT_LITERAL ('0xa_bc.d_efP0F')""");

    doTest("0xa_ap1 0xa_b.P1_2 0x.a_bcP1___23d 0xa_bc.de_fP1_234D",
           """
             DOUBLE_LITERAL ('0xa_ap1')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0xa_b.P1_2')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0x.a_bcP1___23d')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0xa_bc.de_fP1_234D')""");
  }

  public void testMalformedCoinLiterals() {
    doTest("_1 _b ._ 0_ 0_8 0x_f 0b_1 0B2 0x1.0_p-1 1.0e_1022 0._1",
           """
             IDENTIFIER ('_1')
             WHITE_SPACE (' ')
             IDENTIFIER ('_b')
             WHITE_SPACE (' ')
             DOT ('.')
             IDENTIFIER ('_')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0_')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0_8')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0x_f')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0b_1')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0B2')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0x1.0_p-1')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('1.0e_1022')
             WHITE_SPACE (' ')
             DOUBLE_LITERAL ('0._1')""");
  }

  public void testMalformedOperators() {
    doTest("(i > = 0)",
           """
             LPARENTH ('(')
             IDENTIFIER ('i')
             WHITE_SPACE (' ')
             GT ('>')
             WHITE_SPACE (' ')
             EQ ('=')
             WHITE_SPACE (' ')
             INTEGER_LITERAL ('0')
             RPARENTH (')')
             """);
  }

  public void testJava8Tokens() {
    doTest("none :: ->",
           "IDENTIFIER ('none')\nWHITE_SPACE (' ')\nDOUBLE_COLON ('::')\nWHITE_SPACE (' ')\nARROW ('->')");
  }

  public void testUnicodeLiterals() {
    doTest("Ɐ Σx dΦ",
           "IDENTIFIER ('Ɐ')\nWHITE_SPACE (' ')\nIDENTIFIER ('Σx')\nWHITE_SPACE (' ')\nIDENTIFIER ('dΦ')");
  }

  public void testTextBlockLiterals() {
    doTest("\"\"\"\n hi there. \"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\"\\n hi there. \"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\" ')");
    doTest("\"\"\".\\\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\".\\\"\"\" ')");
    doTest("\"\"\".\\\\\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\".\\\\\"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\".\\\\\\\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\".\\\\\\\"\"\" ')");
    doTest("\"\"\"\"\"\"+\"\"\"\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\"\"\"\"')\nPLUS ('+')\nTEXT_BLOCK_LITERAL ('\"\"\"\"\"\"')\nWHITE_SPACE (' ')");
    doTest("\\\"\"\".\"\"\" ", "BAD_CHARACTER ('\\')\nTEXT_BLOCK_LITERAL ('\"\"\".\"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\"\n  \"\\\"\"\"  \"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\"\\n  \"\\\"\"\"  \"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\"\n  \"\"\\\"\"\"  \"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\"\\n  \"\"\\\"\"\"  \"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\" \n\"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\" \\n\"\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\"\n \\u005C\"\"\"\n \"\"\"", "TEXT_BLOCK_LITERAL ('\"\"\"\\n \\u005C\"\"\"\\n \"\"\"')"); // unicode escaped backslash '\'

    doTest("\"\"\"\n ...\n\"\" \"\"\" ", "TEXT_BLOCK_LITERAL ('\"\"\"\\n ...\\n\"\" \"\"\"')\nWHITE_SPACE (' ')");
  }

  public void testStringTemplates() {
    doTest("\"\\{}\"", "STRING_TEMPLATE_BEGIN ('\"\\{')\nSTRING_TEMPLATE_END ('}\"')");
    doTest("\"\"\"\n\\{}\"\"\"", "TEXT_BLOCK_TEMPLATE_BEGIN ('\"\"\"\\n\\{')\nTEXT_BLOCK_TEMPLATE_END ('}\"\"\"')");
    doTest("\"\\{123}\"", "STRING_TEMPLATE_BEGIN ('\"\\{')\nINTEGER_LITERAL ('123')\nSTRING_TEMPLATE_END ('}\"')");
    doTest("\"\"\"\n\\{123}\"\"\"", "TEXT_BLOCK_TEMPLATE_BEGIN ('\"\"\"\\n\\{')\nINTEGER_LITERAL ('123')\nTEXT_BLOCK_TEMPLATE_END ('}\"\"\"')");
    doTest("\"\\{new int[][] {{}}}\"", """
      STRING_TEMPLATE_BEGIN ('"\\{')
      NEW_KEYWORD ('new')
      WHITE_SPACE (' ')
      INT_KEYWORD ('int')
      LBRACKET ('[')
      RBRACKET (']')
      LBRACKET ('[')
      RBRACKET (']')
      WHITE_SPACE (' ')
      LBRACE ('{')
      LBRACE ('{')
      RBRACE ('}')
      RBRACE ('}')
      STRING_TEMPLATE_END ('}"')""");
    doTest("\"\"\"\n\\{new int[][] {{}}}\"\"\"", """
      TEXT_BLOCK_TEMPLATE_BEGIN ('""\"\\n\\{')
      NEW_KEYWORD ('new')
      WHITE_SPACE (' ')
      INT_KEYWORD ('int')
      LBRACKET ('[')
      RBRACKET (']')
      LBRACKET ('[')
      RBRACKET (']')
      WHITE_SPACE (' ')
      LBRACE ('{')
      LBRACE ('{')
      RBRACE ('}')
      RBRACE ('}')
      TEXT_BLOCK_TEMPLATE_END ('}""\"')""");
    doTest("\"\\{x} + \\{y} = \\{x + y}\"", """
      STRING_TEMPLATE_BEGIN ('"\\{')
      IDENTIFIER ('x')
      STRING_TEMPLATE_MID ('} + \\{')
      IDENTIFIER ('y')
      STRING_TEMPLATE_MID ('} = \\{')
      IDENTIFIER ('x')
      WHITE_SPACE (' ')
      PLUS ('+')
      WHITE_SPACE (' ')
      IDENTIFIER ('y')
      STRING_TEMPLATE_END ('}"')""");
    doTest("\"\"\"\n\\{x} +\n \\{y} = \\{x + y}\"\"\"", """
      TEXT_BLOCK_TEMPLATE_BEGIN ('""\"\\n\\{')
      IDENTIFIER ('x')
      TEXT_BLOCK_TEMPLATE_MID ('} +\\n \\{')
      IDENTIFIER ('y')
      TEXT_BLOCK_TEMPLATE_MID ('} = \\{')
      IDENTIFIER ('x')
      WHITE_SPACE (' ')
      PLUS ('+')
      WHITE_SPACE (' ')
      IDENTIFIER ('y')
      TEXT_BLOCK_TEMPLATE_END ('}""\"')""");
    doTest("\"\\{}\" }\"", """
      STRING_TEMPLATE_BEGIN ('"\\{')
      STRING_TEMPLATE_END ('}"')
      WHITE_SPACE (' ')
      RBRACE ('}')
      STRING_LITERAL ('"')""");
    doTest("\"\\{\"hello\"}\" ", "STRING_TEMPLATE_BEGIN ('\"\\{')\nSTRING_LITERAL ('\"hello\"')\nSTRING_TEMPLATE_END ('}\"')\nWHITE_SPACE (' ')");
    doTest("\"\"\"\n        \\{\"\"\"\n!\"\"\" }\"\"\"  ", """
      TEXT_BLOCK_TEMPLATE_BEGIN ('""\"\\n        \\{')
      TEXT_BLOCK_LITERAL ('""\"\\n!""\"')
      WHITE_SPACE (' ')
      TEXT_BLOCK_TEMPLATE_END ('}""\"')
      WHITE_SPACE ('  ')""");
    doTest("""
             "\\{fruit[0]}, \\{STR."\\{fruit[1]}, \\{fruit[2]}"}"
             """,
           """
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('fruit')
             LBRACKET ('[')
             INTEGER_LITERAL ('0')
             RBRACKET (']')
             STRING_TEMPLATE_MID ('}, \\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('fruit')
             LBRACKET ('[')
             INTEGER_LITERAL ('1')
             RBRACKET (']')
             STRING_TEMPLATE_MID ('}, \\{')
             IDENTIFIER ('fruit')
             LBRACKET ('[')
             INTEGER_LITERAL ('2')
             RBRACKET (']')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             WHITE_SPACE ('\\n')""");
    doTest("""
             STR."\\{STR."\\{STR."\\{STR."\\{STR."\\{STR."\\{STR.""}"}"}"}"}"}"
             """,
           """
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_LITERAL ('""')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             STRING_TEMPLATE_END ('}"')
             WHITE_SPACE ('\\n')""");
    doTest("""
             ""\"
                   "\\{}""\"""",
           """
             TEXT_BLOCK_TEMPLATE_BEGIN ('""\"\\n      "\\{')
             TEXT_BLOCK_TEMPLATE_END ('}""\"')""");
    doTest("""
             STR.""\"
             xx
             \\{STR."String \\{a} String"}
             xx""\"""",
           """
             IDENTIFIER ('STR')
             DOT ('.')
             TEXT_BLOCK_TEMPLATE_BEGIN ('""\"\\nxx\\n\\{')
             IDENTIFIER ('STR')
             DOT ('.')
             STRING_TEMPLATE_BEGIN ('"String \\{')
             IDENTIFIER ('a')
             STRING_TEMPLATE_END ('} String"')
             TEXT_BLOCK_TEMPLATE_END ('}\\nxx""\"')""");
  }

  public void testStringLiterals() {
    doTest("\"", "STRING_LITERAL ('\"')");
    doTest("\" ", "STRING_LITERAL ('\" ')");
    doTest("\"\"", "STRING_LITERAL ('\"\"')");
    doTest("\"\" ", "STRING_LITERAL ('\"\"')\nWHITE_SPACE (' ')");
    doTest("\"x\n ", "STRING_LITERAL ('\"x')\nWHITE_SPACE ('\\n ')");
    doTest("\"\\\"\" ", "STRING_LITERAL ('\"\\\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\\", "STRING_LITERAL ('\"\\')");
    doTest("\"\\u", "STRING_LITERAL ('\"\\u')");
    doTest("\"\n\"", "STRING_LITERAL ('\"')\nWHITE_SPACE ('\\n')\nSTRING_LITERAL ('\"')");
    doTest("\"\\n\" ", "STRING_LITERAL ('\"\\n\"')\nWHITE_SPACE (' ')");
    doTest("\"\\uuuuuu005c\"\" ", "STRING_LITERAL ('\"\\uuuuuu005c\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\\u005c\"\" ", "STRING_LITERAL ('\"\\u005c\"\"')\nWHITE_SPACE (' ')");
    doTest("\"\\u005\" ", "STRING_LITERAL ('\"\\u005\"')\nWHITE_SPACE (' ')"); // broken unicode escape
    doTest("\"\\u00\" ", "STRING_LITERAL ('\"\\u00\"')\nWHITE_SPACE (' ')"); // broken unicode escape
    doTest("\"\\u0\" ", "STRING_LITERAL ('\"\\u0\"')\nWHITE_SPACE (' ')"); // broken unicode escape
    doTest("\"\\u\" ", "STRING_LITERAL ('\"\\u\"')\nWHITE_SPACE (' ')"); // broken unicode escape

    // see also com.intellij.java.codeInsight.daemon.LightAdvHighlightingTest#testStringLiterals
    doTest(" \"\\u000a\" ", "WHITE_SPACE (' ')\nSTRING_LITERAL ('\"\\u000a\"')\nWHITE_SPACE (' ')");
  }

  public void testCharLiterals() {
    doTest("'\\u005c\\u005c'", "CHARACTER_LITERAL (''\\u005c\\u005c'')"); // unicode escaped escaped slash '\\'
    doTest("\\u0027\\u005c\\u005c'", "CHARACTER_LITERAL ('\\u0027\\u005c\\u005c'')"); // unicode escaped escaped slash '\\'
    doTest("'\\u1234' ", "CHARACTER_LITERAL (''\\u1234'')\nWHITE_SPACE (' ')");
    doTest("'\\u1234\\u0027 ", "CHARACTER_LITERAL (''\\u1234\\u0027')\nWHITE_SPACE (' ')");
    doTest("'x' ", "CHARACTER_LITERAL (''x'')\nWHITE_SPACE (' ')");
    doTest("'", "CHARACTER_LITERAL (''')");
    doTest("\\u0027", "CHARACTER_LITERAL ('\\u0027')");
    doTest("' ", "CHARACTER_LITERAL ('' ')");
    doTest("'\\u0020", "CHARACTER_LITERAL (''\\u0020')");
    doTest("''", "CHARACTER_LITERAL ('''')");
    doTest("'\\u0027", "CHARACTER_LITERAL (''\\u0027')");
    doTest("\\u0027\\u0027", "CHARACTER_LITERAL ('\\u0027\\u0027')");
    doTest("'\\u007F' 'F' ", "CHARACTER_LITERAL (''\\u007F'')\nWHITE_SPACE (' ')\nCHARACTER_LITERAL (''F'')\nWHITE_SPACE (' ')");
    doTest("'\\u005C' ", "CHARACTER_LITERAL (''\\u005C' ')"); // closing quote is escaped with unicode escaped backslash
  }

  public void testComments() {
    doTest("//", "END_OF_LINE_COMMENT ('//')");
    doTest("\\u002f\\u002F", "END_OF_LINE_COMMENT ('\\u002f\\u002F')");
    doTest("//\n", "END_OF_LINE_COMMENT ('//')\nWHITE_SPACE ('\\n')");
    doTest("//\\u000A", "END_OF_LINE_COMMENT ('//')\nWHITE_SPACE ('\\u000A')");
    doTest("//x\n", "END_OF_LINE_COMMENT ('//x')\nWHITE_SPACE ('\\n')");
    doTest("/\\u002fx\n", "END_OF_LINE_COMMENT ('/\\u002fx')\nWHITE_SPACE ('\\n')");
    doTest("/*/ ", "C_STYLE_COMMENT ('/*/ ')");
    doTest("/\\u002A/ ", "C_STYLE_COMMENT ('/\\u002A/ ')");
    doTest("/**/ ", "C_STYLE_COMMENT ('/**/')\nWHITE_SPACE (' ')");
    doTest("\\u002f**/ ", "C_STYLE_COMMENT ('\\u002f**/')\nWHITE_SPACE (' ')");
    doTest("/*x*/ ", "C_STYLE_COMMENT ('/*x*/')\nWHITE_SPACE (' ')");
    doTest("/*x\\u002a\\u002F\\u0020", "C_STYLE_COMMENT ('/*x\\u002a\\u002F')\nWHITE_SPACE ('\\u0020')");
    doTest("/***/ ", "DOC_COMMENT ('/***/')\nWHITE_SPACE (' ')");
    doTest("/*\\u002a*/ ", "DOC_COMMENT ('/*\\u002a*/')\nWHITE_SPACE (' ')");
    doTest("/**x*/ ", "DOC_COMMENT ('/**x*/')\nWHITE_SPACE (' ')");
    doTest("/**\\u0078*/ ", "DOC_COMMENT ('/**\\u0078*/')\nWHITE_SPACE (' ')");
    doTest("/*", "C_STYLE_COMMENT ('/*')");
    doTest("/\\u002a", "C_STYLE_COMMENT ('/\\u002a')");
    doTest("/**", "DOC_COMMENT ('/**')");
    doTest("/*\\u002a", "DOC_COMMENT ('/*\\u002a')");
    doTest("/***", "DOC_COMMENT ('/***')");
    doTest("/**\\u002a", "DOC_COMMENT ('/**\\u002a')");
    doTest("#! ", "END_OF_LINE_COMMENT ('#! ')");
    doTest("\\u0023! ", "BAD_CHARACTER ('\\')\nIDENTIFIER ('u0023')\nEXCL ('!')\nWHITE_SPACE (' ')");
    doTest("/", "DIV ('/')");
    doTest("1/2", "INTEGER_LITERAL ('1')\nDIV ('/')\nINTEGER_LITERAL ('2')");
    doTest("//\\\\u000A test", "END_OF_LINE_COMMENT ('//\\\\u000A test')"); // escaped backslash, not a unicode escape
    doTest("//\\\\\\u000A test",
           "END_OF_LINE_COMMENT ('//\\\\')\nWHITE_SPACE ('\\u000A ')\nIDENTIFIER ('test')"); // escaped backslash, followed by a unicode escape
  }

  public void testWhitespace() {
    doTest(" ", "WHITE_SPACE (' ')");
    doTest("\t", "WHITE_SPACE ('\t')");
    doTest("\n", "WHITE_SPACE ('\\n')");
    doTest("\r", "WHITE_SPACE ('\r')");
    doTest("\\u000A", "WHITE_SPACE ('\\u000A')");
    doTest("\\u000d", "WHITE_SPACE ('\\u000d')");
    doTest("\\u000d\n\\u000a", "WHITE_SPACE ('\\u000d\\n\\u000a')");
    doTest("\\", "BAD_CHARACTER ('\\')");
    doTest("\\u", "BAD_CHARACTER ('\\')\nIDENTIFIER ('u')");
    doTest("\\u0", "BAD_CHARACTER ('\\')\nIDENTIFIER ('u0')");
    doTest("\\u00", "BAD_CHARACTER ('\\')\nIDENTIFIER ('u00')");
    doTest("\\u000", "BAD_CHARACTER ('\\')\nIDENTIFIER ('u000')");
    doTest("\\u000A", "WHITE_SPACE ('\\u000A')");
    doTest("\\u000A\\u000A", "WHITE_SPACE ('\\u000A\\u000A')");
    doTest("\\\\u000A", "BAD_CHARACTER ('\\')\nBAD_CHARACTER ('\\')\nIDENTIFIER ('u000A')");
    doTest("\\\\\\u000A", "BAD_CHARACTER ('\\')\nBAD_CHARACTER ('\\')\nWHITE_SPACE ('\\u000A')");
  }

  @Override
  protected @NotNull String getDirPath() {
    return "";
  }
}