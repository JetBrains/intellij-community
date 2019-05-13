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
package org.jetbrains.lang.manifest;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightPlatformTestCase;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestParserTest extends LightIdeaTestCase {
  public void testEmpty() {
    doTest("",

           "ManifestFile:MANIFEST.MF\n" +
           "  <empty list>\n");
  }

  public void testSpaces() {
    doTest("  ",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    PsiErrorElement:Header expected\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      ManifestToken:HEADER_VALUE_PART_TOKEN(' ')\n");
  }

  public void testRandomText() {
    doTest("some text\nmore text",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:some text\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('some text')\n" +
           "      PsiErrorElement:':' expected\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    Header:more text\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('more text')\n" +
           "      PsiErrorElement:':' expected\n" +
           "        <empty list>\n");
  }

  public void testNoHeader() {
    doTest(" some text",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    PsiErrorElement:Header expected\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      ManifestToken:HEADER_VALUE_PART_TOKEN('some text')\n");
  }

  public void testHeaderBreak1() {
    doTest("Header\n value",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Header\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Header')\n" +
           "      PsiErrorElement:':' expected\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "        ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n");
  }

  public void testHeaderBreak2() {
    doTest("Header:\n value",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Header\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Header')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "        ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n");
  }

  public void testHeaderBreak3() {
    doTest("Header: \n value",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Header\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Header')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "        ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n");
  }

  public void testBadHeaderStart() {
    doTest("Name: ab;dir:\n" +
           "=value;a:=b\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Name\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Name')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('ab')\n" +
           "        ManifestToken:SEMICOLON_TOKEN(';')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('dir')\n" +
           "        ManifestToken:COLON_TOKEN(':')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    Header:=value;a\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('=value;a')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:EQUALS_TOKEN('=')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('b')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testNewLines() {
    doTest("\n\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n" +
           "  Section\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n");
  }

  public void testSimple() {
    doTest("Manifest-Version: 1.0\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testExtraSpaceInHeaderAssignment() {
    doTest("Manifest-Version : 1.0\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version \n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version ')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testMissingSpaceInHeaderAssignment() {
    doTest("Specification-Vendor:name\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Specification-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Specification-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('name')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleWithNewLines() {
    doTest("Manifest-Version: 1.0\n\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n");
  }

  public void testSimpleIncomplete() {
    doTest("Manifest-Version:",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        <empty list>\n");
  }

  public void testSimpleIncompleteWithNewLine() {
    doTest("Manifest-Version:\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleIncompleteWithNewLines() {
    doTest("Manifest-Version:\n\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      PsiErrorElement:Whitespace expected\n" +
           "        <empty list>\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n");
  }

  public void testSimpleWithContinuation() {
    doTest("Specification-Vendor: Acme\n" +
           " Company\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Specification-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Specification-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('Acme')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "        ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('Company')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleWithQuotedValue() {
    doTest("Implementation-Vendor: \"Apache Software Foundation\"\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Implementation-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:QUOTE_TOKEN('\"')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Software Foundation')\n" +
           "        ManifestToken:QUOTE_TOKEN('\"')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleHeaderValueStartsWithColon() {
    doTest("Implementation-Vendor: :value\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Implementation-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:COLON_TOKEN(':')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleHeaderValueStartsWithEquals() {
    doTest("Implementation-Vendor: =value\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Implementation-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:EQUALS_TOKEN('=')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testSimpleHeaderValueStartsWithSemicolon() {
    doTest("Implementation-Vendor: ;value\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Implementation-Vendor\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:SEMICOLON_TOKEN(';')\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testTwoHeaders() {
    doTest("Manifest-Version: 1.0\n" +
           "Ant-Version: Apache Ant 1.6.5\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    Header:Ant-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Ant-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Ant 1.6.5')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  public void testTwoSections() {
    doTest("Manifest-Version: 1.0\n" +
           "\n" +
           "Ant-Version: Apache Ant 1.6.5\n\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    Header:Manifest-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n" +
           "  Section\n" +
           "    Header:Ant-Version\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Ant-Version')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Ant 1.6.5')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n");
  }

  public void testEmptyMainSection() {
    doTest("\nHeader: value\n",

           "ManifestFile:MANIFEST.MF\n" +
           "  Section\n" +
           "    ManifestToken:SECTION_END_TOKEN('\\n')\n" +
           "  Section\n" +
           "    Header:Header\n" +
           "      ManifestToken:HEADER_NAME_TOKEN('Header')\n" +
           "      ManifestToken:COLON_TOKEN(':')\n" +
           "      ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')\n" +
           "      HeaderValuePart\n" +
           "        ManifestToken:HEADER_VALUE_PART_TOKEN('value')\n" +
           "        ManifestToken:NEWLINE_TOKEN('\\n')\n");
  }

  private static void doTest(String source, String expected) {
    PsiFile file = LightPlatformTestCase.createLightFile("MANIFEST.MF", source);
    assertEquals(expected, DebugUtil.psiToString(file, true));
  }
}
