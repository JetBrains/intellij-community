/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.lang.manifest.parser.ManifestLexer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
@SuppressWarnings("UnnecessaryStringEscape")
public class ManifestLexerTest {
  @Test
  public void testSpaces() {
    doTest("   ",

           "SIGNIFICANT_SPACE_TOKEN (' ')\n" +
           "HEADER_VALUE_PART_TOKEN ('  ')");
  }

  @Test
  public void testRandomText() {
    doTest("some text\nsome more text",

           """
             HEADER_NAME_TOKEN ('some text')
             NEWLINE_TOKEN ('\n')
             HEADER_NAME_TOKEN ('some more text')""");
  }

  @Test
  public void testValid() {
    doTest("Name: Value",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value')""");
  }

  @Test
  public void testInvalidSpaceBeforeColon() {
    doTest("Name : Value",

           """
             HEADER_NAME_TOKEN ('Name ')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value')""");
  }

  @Test
  public void testMissingSpaceAfterColon() {
    doTest("Name:Value",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             HEADER_VALUE_PART_TOKEN ('Value')""");
  }

  @Test
  public void testTwoHeaders() {
    doTest("Name: Value\nName2: Value2",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value')
             NEWLINE_TOKEN ('\n')
             HEADER_NAME_TOKEN ('Name2')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value2')""");
  }

  @Test
  public void testContinuation() {
    doTest("Name: Value\n Value2",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value')
             NEWLINE_TOKEN ('\n')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value2')""");
  }

  @Test
  public void testSection() {
    doTest("Name: Value\n\nName2: Value2",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value')
             NEWLINE_TOKEN ('\n')
             SECTION_END_TOKEN ('\n')
             HEADER_NAME_TOKEN ('Name2')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value2')""");
  }

  @Test
  public void testNoIgnoredSpaces() {
    doTest("Name: Value \n   Value2",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('Value ')
             NEWLINE_TOKEN ('\n')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('  Value2')""");
  }

  @Test
  public void testSpecialCharacters() {
    doTest("Name: ;:=,\"",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             SEMICOLON_TOKEN (';')
             COLON_TOKEN (':')
             EQUALS_TOKEN ('=')
             COMMA_TOKEN (',')
             QUOTE_TOKEN ('"')""");
  }

  @Test
  public void testErrorEndsAtNewline() {
    doTest("Name \n value",

           """
             HEADER_NAME_TOKEN ('Name ')
             NEWLINE_TOKEN ('\n')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('value')""");
  }

  @Test
  public void testNewlineBetweenSpecialChars() {
    doTest("Name: ab;dir:\n =value\n",

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('ab')
             SEMICOLON_TOKEN (';')
             HEADER_VALUE_PART_TOKEN ('dir')
             COLON_TOKEN (':')
             NEWLINE_TOKEN ('\n')
             SIGNIFICANT_SPACE_TOKEN (' ')
             EQUALS_TOKEN ('=')
             HEADER_VALUE_PART_TOKEN ('value')
             NEWLINE_TOKEN ('\n')""");
  }

  @Test
  public void testBadHeaderStart() {
    doTest("""
             Name: ab;dir:
             =value;a:=b
             """,

           """
             HEADER_NAME_TOKEN ('Name')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('ab')
             SEMICOLON_TOKEN (';')
             HEADER_VALUE_PART_TOKEN ('dir')
             COLON_TOKEN (':')
             NEWLINE_TOKEN ('\n')
             HEADER_NAME_TOKEN ('=value;a')
             COLON_TOKEN (':')
             EQUALS_TOKEN ('=')
             HEADER_VALUE_PART_TOKEN ('b')
             NEWLINE_TOKEN ('\n')""");
  }

  @Test
  public void testBadValueSplit() {
    doTest("""
             Export-Package: org.osgi.framework.start
             level;uses:="org.osgi.framework"
             """,

           """
             HEADER_NAME_TOKEN ('Export-Package')
             COLON_TOKEN (':')
             SIGNIFICANT_SPACE_TOKEN (' ')
             HEADER_VALUE_PART_TOKEN ('org.osgi.framework.start')
             NEWLINE_TOKEN ('\n')
             HEADER_NAME_TOKEN ('level;uses')
             COLON_TOKEN (':')
             EQUALS_TOKEN ('=')
             QUOTE_TOKEN ('"')
             HEADER_VALUE_PART_TOKEN ('org.osgi.framework')
             QUOTE_TOKEN ('"')
             NEWLINE_TOKEN ('\n')""");
  }

  private static void doTest(String text, String expected) {
    ManifestLexer lexer = new ManifestLexer();
    lexer.start(text);

    StringBuilder actual = new StringBuilder();
    IElementType token;
    while ((token = lexer.getTokenType()) != null) {
      actual.append(token).append(" ('").append(lexer.getTokenText()).append("')\n");
      lexer.advance();
    }

    assertEquals(expected.trim(), actual.toString().trim());
  }
}
