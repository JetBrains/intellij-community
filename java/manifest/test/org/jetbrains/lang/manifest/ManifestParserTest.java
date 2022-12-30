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

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestParserTest extends LightIdeaTestCase {
  public void testEmpty() {
    doTest("",

           """
             ManifestFile:MANIFEST.MF
               <empty list>
             """);
  }

  public void testSpaces() {
    doTest("  ",

           """
             ManifestFile:MANIFEST.MF
               Section
                 PsiErrorElement:Header expected
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   ManifestToken:HEADER_VALUE_PART_TOKEN(' ')
             """);
  }

  public void testRandomText() {
    doTest("some text\nmore text",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:some text
                   ManifestToken:HEADER_NAME_TOKEN('some text')
                   PsiErrorElement:':' expected
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 Header:more text
                   ManifestToken:HEADER_NAME_TOKEN('more text')
                   PsiErrorElement:':' expected
                     <empty list>
             """);
  }

  public void testNoHeader() {
    doTest(" some text",

           """
             ManifestFile:MANIFEST.MF
               Section
                 PsiErrorElement:Header expected
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   ManifestToken:HEADER_VALUE_PART_TOKEN('some text')
             """);
  }

  public void testHeaderBreak1() {
    doTest("Header\n value",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Header
                   ManifestToken:HEADER_NAME_TOKEN('Header')
                   PsiErrorElement:':' expected
                     ManifestToken:NEWLINE_TOKEN('\\n')
                     ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
             """);
  }

  public void testHeaderBreak2() {
    doTest("Header:\n value",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Header
                   ManifestToken:HEADER_NAME_TOKEN('Header')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     ManifestToken:NEWLINE_TOKEN('\\n')
                     ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
             """);
  }

  public void testHeaderBreak3() {
    doTest("Header: \n value",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Header
                   ManifestToken:HEADER_NAME_TOKEN('Header')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:NEWLINE_TOKEN('\\n')
                     ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
             """);
  }

  public void testBadHeaderStart() {
    doTest("""
             Name: ab;dir:
             =value;a:=b
             """,

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Name
                   ManifestToken:HEADER_NAME_TOKEN('Name')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('ab')
                     ManifestToken:SEMICOLON_TOKEN(';')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('dir')
                     ManifestToken:COLON_TOKEN(':')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 Header:=value;a
                   ManifestToken:HEADER_NAME_TOKEN('=value;a')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     ManifestToken:EQUALS_TOKEN('=')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('b')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testNewLines() {
    doTest("\n\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 ManifestToken:SECTION_END_TOKEN('\\n')
               Section
                 ManifestToken:SECTION_END_TOKEN('\\n')
             """);
  }

  public void testSimple() {
    doTest("Manifest-Version: 1.0\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testExtraSpaceInHeaderAssignment() {
    doTest("Manifest-Version : 1.0\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version\s
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version ')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testMissingSpaceInHeaderAssignment() {
    doTest("Specification-Vendor:name\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Specification-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Specification-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('name')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleWithNewLines() {
    doTest("Manifest-Version: 1.0\n\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 ManifestToken:SECTION_END_TOKEN('\\n')
             """);
  }

  public void testSimpleIncomplete() {
    doTest("Manifest-Version:",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     <empty list>
             """);
  }

  public void testSimpleIncompleteWithNewLine() {
    doTest("Manifest-Version:\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleIncompleteWithNewLines() {
    doTest("Manifest-Version:\n\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   PsiErrorElement:Whitespace expected
                     <empty list>
                   HeaderValuePart
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 ManifestToken:SECTION_END_TOKEN('\\n')
             """);
  }

  public void testSimpleWithContinuation() {
    doTest("""
             Specification-Vendor: Acme
              Company
             """,

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Specification-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Specification-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('Acme')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                     ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('Company')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleWithQuotedValue() {
    doTest("Implementation-Vendor: \"Apache Software Foundation\"\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Implementation-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:QUOTE_TOKEN('"')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Software Foundation')
                     ManifestToken:QUOTE_TOKEN('"')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleHeaderValueStartsWithColon() {
    doTest("Implementation-Vendor: :value\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Implementation-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:COLON_TOKEN(':')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleHeaderValueStartsWithEquals() {
    doTest("Implementation-Vendor: =value\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Implementation-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:EQUALS_TOKEN('=')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testSimpleHeaderValueStartsWithSemicolon() {
    doTest("Implementation-Vendor: ;value\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Implementation-Vendor
                   ManifestToken:HEADER_NAME_TOKEN('Implementation-Vendor')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:SEMICOLON_TOKEN(';')
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testTwoHeaders() {
    doTest("""
             Manifest-Version: 1.0
             Ant-Version: Apache Ant 1.6.5
             """,

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 Header:Ant-Version
                   ManifestToken:HEADER_NAME_TOKEN('Ant-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Ant 1.6.5')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  public void testTwoSections() {
    doTest("""
             Manifest-Version: 1.0

             Ant-Version: Apache Ant 1.6.5

             """,

           """
             ManifestFile:MANIFEST.MF
               Section
                 Header:Manifest-Version
                   ManifestToken:HEADER_NAME_TOKEN('Manifest-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('1.0')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 ManifestToken:SECTION_END_TOKEN('\\n')
               Section
                 Header:Ant-Version
                   ManifestToken:HEADER_NAME_TOKEN('Ant-Version')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('Apache Ant 1.6.5')
                     ManifestToken:NEWLINE_TOKEN('\\n')
                 ManifestToken:SECTION_END_TOKEN('\\n')
             """);
  }

  public void testEmptyMainSection() {
    doTest("\nHeader: value\n",

           """
             ManifestFile:MANIFEST.MF
               Section
                 ManifestToken:SECTION_END_TOKEN('\\n')
               Section
                 Header:Header
                   ManifestToken:HEADER_NAME_TOKEN('Header')
                   ManifestToken:COLON_TOKEN(':')
                   ManifestToken:SIGNIFICANT_SPACE_TOKEN(' ')
                   HeaderValuePart
                     ManifestToken:HEADER_VALUE_PART_TOKEN('value')
                     ManifestToken:NEWLINE_TOKEN('\\n')
             """);
  }

  private void doTest(String source, String expected) {
    PsiFile file = createLightFile("MANIFEST.MF", source);
    assertEquals(expected, DebugUtil.psiToString(file, false));
  }
}
