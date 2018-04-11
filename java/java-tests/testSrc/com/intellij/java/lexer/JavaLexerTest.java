// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LexerTestCase;

public class JavaLexerTest extends LexerTestCase {
  public void testClassicNumericLiterals() {
    doTest("0 1234 01234 0x1234",
           "INTEGER_LITERAL ('0')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('1234')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('01234')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0x1234')");

    doTest("0L 1234l 01234L 0x1234l",
           "LONG_LITERAL ('0L')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('1234l')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('01234L')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('0x1234l')");

    doTest("0f 1e1f 2.f .3f 0f 3.14f 6.022137e+23f",
           "FLOAT_LITERAL ('0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('1e1f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('2.f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('.3f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('3.14f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('6.022137e+23f')");

    doTest("0d 1e1 2. .3 0.0 3.14 1e-9d 1e137",
           "DOUBLE_LITERAL ('0d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e1')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('2.')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('.3')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0.0')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('3.14')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e-9d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e137')");

    doTest(". e0 x1 ax .e .p 08 .0e .0f",
           "DOT ('.')\nWHITE_SPACE (' ')\n" +
           "IDENTIFIER ('e0')\nWHITE_SPACE (' ')\n" +
           "IDENTIFIER ('x1')\nWHITE_SPACE (' ')\n" +
           "IDENTIFIER ('ax')\nWHITE_SPACE (' ')\n" +
           "DOT ('.')\nIDENTIFIER ('e')\nWHITE_SPACE (' ')\n" +
           "DOT ('.')\nIDENTIFIER ('p')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('08')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('.0e')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('.0f')");
  }

  public void testTigerNumericLiterals() {
    doTest("0xap0f 0xab.p0F 0x.abcP0f 0xabc.defP0F",
           "FLOAT_LITERAL ('0xap0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0xab.p0F')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0x.abcP0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0xabc.defP0F')");

    doTest("0xap1 0xab.P12 0x.abcP123d 0xabc.defP1234D",
           "DOUBLE_LITERAL ('0xap1')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0xab.P12')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0x.abcP123d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0xabc.defP1234D')");

    doTest("p0",
           "IDENTIFIER ('p0')");
  }

  public void testCoinNumericLiterals() {
    doTest("1_2 0_1 012__34 0x1_2_3_4 0B0 0b0001_0010_0100_1000",
           "INTEGER_LITERAL ('1_2')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0_1')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('012__34')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0x1_2_3_4')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0B0')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0b0001_0010_0100_1000')");

    doTest("1_2L 0_7l 012__34l 0x1_2_3_4L 0B0L 0b0001_0010_0100_1000l",
           "LONG_LITERAL ('1_2L')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('0_7l')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('012__34l')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('0x1_2_3_4L')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('0B0L')\nWHITE_SPACE (' ')\n" +
           "LONG_LITERAL ('0b0001_0010_0100_1000l')");

    doTest("1_0f 1e1_2f 2_2.f .3_3f 3.14_16f 6.022___137e+2_3f",
           "FLOAT_LITERAL ('1_0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('1e1_2f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('2_2.f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('.3_3f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('3.14_16f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('6.022___137e+2_3f')");

    doTest("0_0d 1e1_1 2_2. .3_3 3.141_592 1e-9_9d 1e1__3_7",
           "DOUBLE_LITERAL ('0_0d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e1_1')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('2_2.')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('.3_3')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('3.141_592')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e-9_9d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1e1__3_7')");

    doTest("0xa_ap1_0f 0xa_b.p22F 0x.ab__cP0f 0xa_bc.d_efP0F",
           "FLOAT_LITERAL ('0xa_ap1_0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0xa_b.p22F')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0x.ab__cP0f')\nWHITE_SPACE (' ')\n" +
           "FLOAT_LITERAL ('0xa_bc.d_efP0F')");

    doTest("0xa_ap1 0xa_b.P1_2 0x.a_bcP1___23d 0xa_bc.de_fP1_234D",
           "DOUBLE_LITERAL ('0xa_ap1')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0xa_b.P1_2')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0x.a_bcP1___23d')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0xa_bc.de_fP1_234D')");
  }

  public void testMalformedCoinLiterals() {
    doTest("_1 _b ._ 0_ 0_8 0x_f 0b_1 0B2 0x1.0_p-1 1.0e_1022 0._1",
           "IDENTIFIER ('_1')\nWHITE_SPACE (' ')\n" +
           "IDENTIFIER ('_b')\nWHITE_SPACE (' ')\n" +
           "DOT ('.')\nIDENTIFIER ('_')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0_')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0_8')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0x_f')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0b_1')\nWHITE_SPACE (' ')\n" +
           "INTEGER_LITERAL ('0B2')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0x1.0_p-1')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('1.0e_1022')\nWHITE_SPACE (' ')\n" +
           "DOUBLE_LITERAL ('0._1')");
  }

  public void testMalformedOperators() {
    doTest("(i > = 0)",
           "LPARENTH ('(')\nIDENTIFIER ('i')\nWHITE_SPACE (' ')\n" +
           "GT ('>')\nWHITE_SPACE (' ')\nEQ ('=')\n" +
           "WHITE_SPACE (' ')\nINTEGER_LITERAL ('0')\nRPARENTH (')')\n");
  }

  public void testJava8Tokens() {
    doTest("none :: ->",
           "IDENTIFIER ('none')\nWHITE_SPACE (' ')\nDOUBLE_COLON ('::')\nWHITE_SPACE (' ')\nARROW ('->')");
  }

  public void testUnicodeLiterals() {
    doTest("Ɐ Σx dΦ",
           "IDENTIFIER ('Ɐ')\nWHITE_SPACE (' ')\nIDENTIFIER ('Σx')\nWHITE_SPACE (' ')\nIDENTIFIER ('dΦ')");
  }

  public void testRawLiterals() {
    doTest(" ``.`.`` ", "WHITE_SPACE (' ')\nRAW_STRING_LITERAL ('``.`.``')\nWHITE_SPACE (' ')");
    doTest(" ``.```.`` ", "WHITE_SPACE (' ')\nRAW_STRING_LITERAL ('``.```.``')\nWHITE_SPACE (' ')");
    doTest(" ``.`.` ", "WHITE_SPACE (' ')\nRAW_STRING_LITERAL ('``.`.` ')");
    doTest(" ``.`.``` ", "WHITE_SPACE (' ')\nRAW_STRING_LITERAL ('``.`.``` ')");
  }

  @Override
  protected Lexer createLexer() {
    return JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
  }

  @Override
  protected String getDirPath() {
    return "";
  }
}