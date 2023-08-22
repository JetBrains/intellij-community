// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.regexp.inspection.AnonymousGroupInspection;
import org.intellij.lang.regexp.inspection.RegExpSimplifiableInspection;
import org.intellij.lang.regexp.inspection.UnexpectedAnchorInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpHighlightingTest extends LightJavaCodeInsightFixtureTestCase {

  public void testDuplicateNamedGroup() {
    doTest("(?<name>abc)(?<<error descr=\"Group with name 'name' already defined\">name</error>>xyz)");
  }

  public void testAnonymousCapturingGroupInspection() {
    myFixture.enableInspections(new AnonymousGroupInspection());
    doTest("<warning descr=\"Anonymous capturing group\">(</warning>moo)<warning descr=\"Numeric back reference\">\\1</warning>");
  }

  public void testWhiteSpaceProperty() {
    // needs only partial escaping
    @NonNls String code = "<weak_warning descr=\"'\\\\P{IsBlank}' can be simplified to '[^ \\t]'\">\\\\P{IsBlank}</weak_warning>";
    myFixture.enableInspections(new RegExpSimplifiableInspection());
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {{ java.util.regex.Pattern.compile(\"" + code + "\"); }}");
    myFixture.testHighlighting();
  }

  public void testRedundantEscape1() {
    doTest("\\;");
  }

  public void testBoundaries() {
    doTest("\\b <error descr=\"This boundary is not supported in this regex dialect\">\\b{g}</error> \\B \\A \\z \\Z \\G");
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest("\\b \\b{g} \\B \\A \\z \\Z \\G");
  }

  public void testNotDuplicateControlCharacter() {
    doTest("[\\ca\\cb]");
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
    doTest("[\\Qabc?*+.))]][]</warning>\\E]");
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
    doTest("[:xdigit:]+");
  }

  public void testNestedBackReference() {
    doTest("([ab]+=<warning descr=\"Back reference is nested into the capturing group it refers to\">\\1</warning>)");
  }

  public void testNoNPE() {
    doTest("(<error descr=\"')' expected\">\"</error>);}}//");
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
  }

  public void testOptions() {
    doTest("(?i)<error descr=\"Dangling quantifier '+'\">+</error>");
    doTest("(?i)<error descr=\"Dangling quantifier '*'\">*</error>");
    doTest("(?i)<error descr=\"Dangling quantifier '{5,6}'\">{5,6}</error>");
  }

  public void testLookbehind() {
    doTest("(?<!(aa)<error descr=\"* repetition not allowed inside lookbehind\">*</error>)");
    doTest("(?<!(aa)<error descr=\"+ repetition not allowed inside lookbehind\">+</error>)");
    doTest("(?<!(aa)?)");
    doTest("(?<!(aa){2,6})");
    doTest("(one)(?<!<error descr=\"Group reference not allowed inside lookbehind\">\\1</error>)");
  }

  public void testNamedProperties() {
    doTest("\\p{<error descr=\"Unknown character category\">Block</error>}+");
    doTest("\\p{Block=<error descr=\"Property value expected\">}</error>+");
    doTest("\\p{Block=CombiningDiacriticalMarks}+");
    doTest("\\p{blk=CombiningDiacriticalMarks}+");
    doTest("\\p{blk=<error descr=\"Unknown property value\">XXX</error>}+");
    doTest("\\p{script=Cyrillic}+");
    doTest("\\p{SC=Cyrillic}+");
    doTest("\\p{Sc=<error descr=\"Unknown property value\">YYY</error>}+");
    doTest("\\p{general_Category=javaWhitespace}+");
    doTest("\\p{GC=LD}+");
    doTest("\\p{gc=<error descr=\"Unknown property value\">ZZZ</error>}+");
  }

  public void testConditionalExprNegative1() {
    doTestUnexpectedAnchor("c ? \"^Hello$\" : \"^World$\"");
  }

  public void testBinaryExprPositive1() {
    doTestUnexpectedAnchor("\"^good" + warningMarker('$') + "\" + \"" + warningMarker('^') + "luck$\"");
  }

  public void testConditionalExprPositive1() {
    doTestUnexpectedAnchor("c ? \" " + warningMarker('^') + "Hello" + warningMarker('$') + " \" " +
                           ": \" " + warningMarker('^') + "World" + warningMarker('$') + " \"");
  }

  public void testConditionalExprPositive2() {
    doTestUnexpectedAnchor("c ? \" " + warningMarker('^') + "Hello$\" " +
                           ": \"^Have" + warningMarker('$') + "\" + \"" + warningMarker('^') + "Fun$\"");
  }

  public void testConditionalExprPositive3() {
    doTestUnexpectedAnchor("c ? (c ? (c ? \"^go$\" : \"^od" + warningMarker('$') + "\" + \"" + warningMarker('^') + "lu$\") : \"^ck$\") " +
                           ": (c ? \"^and" + warningMarker('$') + "\" + \"" + warningMarker('^') +
                           "ha" + warningMarker('$') + "\" + \"" + warningMarker('^') + "ve$\" : \"^fun$\")");
  }

  public void testConditionalExprPositive4() {
    doTestUnexpectedAnchor("c ? \"^hello" + warningMarker('$') + "\" + getStr() + \"" + warningMarker('^') + "world$\" " +
                           ": getStr() + \"" + warningMarker('^') + "yeah" + warningMarker('$') + " \"");
  }

  public void testConcatenationWithEmptyStrings() {
    // "" + " ^" + "" + "$ "
    doTestUnexpectedAnchor("\"\" +" + "\" " + warningMarker('^') + "\" + " + "\"\" + " + "\"" + warningMarker('$') + " \"");
  }

  private void doTest(@NonNls String code) {
    code = StringUtil.escapeBackSlashes(code);
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {{ java.util.regex.Pattern.compile(\"" + code + "\"); }}");
    myFixture.testHighlighting();
  }

  private void doTestUnexpectedAnchor(@NonNls String code) {
    myFixture.enableInspections(new UnexpectedAnchorInspection());
    myFixture.configureByText(JavaFileType.INSTANCE, "class X {" +
                                                     " void test(boolean c) {" +
                                                     "   java.util.regex.Pattern.compile(" + code + ");" +
                                                     " }" +
                                                     " String getStr() {" +
                                                     "   return \"\";" +
                                                     " }" +
                                                     "}");
    myFixture.testHighlighting();
  }

  private static String warningMarker(char symbol) {
    return String.format("<warning descr=\"Anchor '%s' in unexpected position\">%s</warning>", symbol, symbol);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
