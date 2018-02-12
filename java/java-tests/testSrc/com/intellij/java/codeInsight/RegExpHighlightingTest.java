/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.inspection.AnonymousGroupInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("Annotator")
public class RegExpHighlightingTest extends LightCodeInsightFixtureTestCase {

  public void testDuplicateNamedGroup() {
    doTest("(?<name>abc)(?<<error descr=\"Group with name 'name' already defined\">name</error>>xyz)");
  }

  public void testAnonymousCapturingGroupInspection() {
    myFixture.enableInspections(new AnonymousGroupInspection());
    doTest("<warning descr=\"Anonymous capturing group\">(</warning>moo)<warning descr=\"Numeric back reference\">\\1</warning>");
  }

  public void testSingleRepetition() {
    doTest("a<weak_warning descr=\"Single repetition\">{1}</weak_warning>");
  }

  public void testRedundantEscape1() {
    doTest("\\;");
  }

  public void testBoundaries() {
    doTest("\\b <error descr=\"This boundary is not supported in this regex dialect\">\\b{g}</error> \\B \\A \\z \\Z \\G");
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest("\\b \\b{g} \\B \\A \\z \\Z \\G");
  }

  public void testSimplifiableRange1() {
    doTest("a<weak_warning descr=\"Repetition range replaceable by '?'\">{0,1}</weak_warning>");
  }

  public void testSimplifiableRange2() {
    doTest("a<weak_warning descr=\"Repetition range replaceable by '+'\">{1,}</weak_warning>");
  }

  public void testSimplifiableRange3() {
    doTest("a<weak_warning descr=\"Repetition range replaceable by '*'\">{0,}</weak_warning>");
  }

  public void testFixedRepetitionRange() {
    doTest("a<weak_warning descr=\"Fixed repetition range\">{3,3}</weak_warning>");
  }

  public void testDuplicateCharacterClass() {
    doTest("[\\w-<warning descr=\"Duplicate predefined character class '\\w' inside character class\">\\w</warning>]");
  }

  public void testNoRange() {
    doTest("[\\w-a]");
  }

  public void testUnicodeGrapheme() {
    doTest("<error descr=\"Illegal/unsupported escape sequence\">\\X</error>");
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest("\\X");
  }

  public void testRedundantEscape2() {
    doTest("\\-[\\*\\-\\[\\]\\\\\\+]");
  }

  public void testBoundaryInsideCharacterClass() {
    doTest("[<error descr=\"Illegal/unsupported escape sequence\">\\b</error>]");
  }

  public void testEmptyGroup1() {
    doTest("<warning descr=\"Empty group\">()</warning>");
  }

  public void testEmptyGroup2() {
    doTest("<warning descr=\"Empty group\">(|)</warning>");
  }

  public void testRedundantGroup() {
    doTest("<warning descr=\"Redundant group nesting\">((a))</warning>");
  }

  public void testNamedGroup() {
    doTest("(?<asdf>[a-c])\\1");
  }

  public void testNamedGroupReference() {
    doTest("(?<asdf>;[a-c])\\k<asdf>");
  }

  public void testUnresolvedNamedGroupReference() {
    doTest("\\k<<error descr=\"Unresolved named group reference\">adsf</error>>");
  }

  public void testInvalidGroupName() {
    doTest("(?<<error descr=\"Invalid group name\">important_value1</error>>\\d\\d)");
  }

  public void testValidGroupName() {
    doTest("(?<importantValue1>\\d\\d)");
  }

  public void testRedundantCharacterRange() {
    doTest("[<warning descr=\"Redundant character range\">a-a</warning>]");
  }

  public void testIllegalCharacterRange1() {
    doTest("[<error descr=\"Illegal character range (to < from)\">\\x4a-\\x3f</error>]");
  }

  public void testIllegalCharacterRange2() {
    doTest("[<error descr=\"Illegal character range (to < from)\">\\udbff\\udfff-\\ud800\\udc00</error>]");
  }

  public void testIllegalCharacterRange3() {
    doTest("[<error descr=\"Illegal character range (to < from)\">z-a</error>]");
  }

  public void testIllegalCharacterRange4() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest("[<error descr=\"Illegal character range (to < from)\">\\N{LATIN SMALL LETTER Z}-\\N{LATIN SMALL LETTER A}</error>]");
  }

  public void testLegalCharacterRange() {
    // Cyrillic Capital Letter Zemlya - Unicode Han Character 'to peel, pare' (Unicode Supplementary Character)
    // without code point support 0x20731 wraps to 0x731 which would produce a "Illegal character range (to < from)" error
    doTest("[\\x{A640}-\\x{20731}]");
  }

  public void testQuoted() {
    doTest("[\\Qabc?*+.)<warning descr=\"Duplicate character ')' inside character class\">)</warning>]<warning descr=\"Duplicate character ']' inside character class\">]</warning>[<warning descr=\"Duplicate character ']' inside character class\">]</warning>\\E]");
  }

  public void testValidDanglingMetacharacters() {
    doTest("]}");
  }

  public void testRedundantlyEscapedClosingBrace() {
    doTest("\\]\\}");
  }

  public void testBadEscape1() {
    doTest("<error descr=\"Illegal/unsupported escape sequence\">\\q</error>");
  }

  public void testBadEscape2() {
    doTest("<error descr=\"Illegal/unsupported escape sequence\">\\</error>");
  }

  public void testBadEscape3() {
    doTest("<error descr=\"Illegal/unsupported escape sequence\">\\E</error>");
  }

  public void testBackspaceChar() {
    doTest("\\x08");
  }

  public void testUnicodeEscape() {
    doTest("\\x{100000}");
  }

  public void testBadUnicodeEscape() {
    doTest("<error descr=\"Illegal unicode escape sequence\">\\x{1000000}</error>");
  }

  public void testProperty1() {
    doTest("^\\p{javaJavaIdentifierStart}+\\p{javaJavaIdentifierPart}+$");
  }

  public void testProperty2() {
    doTest("\\p{InArabiC}\\p{IsTitleCase}\\p{IsAlphabetic}\\p{all}\\p{IsGreeK}");
  }

  public void testPosixCharacterClass() {
    // posix character classes are not available in java regex patterns
    doTest("[:xdig<warning descr=\"Duplicate character 'i' inside character class\">i</warning>t<warning descr=\"Duplicate character ':' inside character class\">:</warning>]+");
  }

  public void testNestedBackReference() {
    doTest("([ab]+=<warning descr=\"Back reference is nested into the capturing group it refers to\">\\1</warning>)");
  }

  public void testNoNPE() {
    doTest("<warning descr=\"Empty group\">(</warning><error descr=\"Unclosed group\">\"</error>);}}//");
  }

  public void testBadInlineOption() {
    doTest("(?i<error descr=\"Unknown inline option flag\">Z</error>m)abc");
  }

  public void testEscapedWhitespace() {
    doTest("a\\ b\\ c");
  }

  public void testEscapedWhitespaceCommentMode() {
    doTest("(?x)a\\ b\\ c");
  }

  public void testCountedQuantifier() {
    doTest("a{2147483647}");
    doTest("a{<error descr=\"Repetition value too large\">2147483648</error>}");
    doTest("a{<error descr=\"Illegal repetition range (min > max)\">1,0</error>}");
    doTest("a<weak_warning descr=\"Repetition range replaceable by '*'\">{<error descr=\"Number expected\">,</error>}</weak_warning>");
  }

  public void testOptions() {
    doTest("(?i)<error descr=\"Dangling metacharacter\">+</error>");
    doTest("(?i)<error descr=\"Dangling metacharacter\">*</error>");
    doTest("(?i)<error descr=\"Dangling metacharacter\">{5,6}</error>");
  }

  public void testLookbehind() {
    doTest("(?<!(aa)<error descr=\"* repetition not allowed inside lookbehind\">*</error>)");
    doTest("(?<!(aa)<error descr=\"+ repetition not allowed inside lookbehind\">+</error>)");
    doTest("(?<!(aa)?)");
    doTest("(?<!(aa){2,6})");
    doTest("(one)(?<!<error descr=\"Group reference not allowed inside lookbehind\">\\1</error>)");
  }

  private void doTest(@Language("RegExp") String code) {
    code = StringUtil.escapeBackSlashes(code);
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {{ java.util.regex.Pattern.compile(\"" + code + "\"); }}");
    myFixture.testHighlighting();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
