// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser;

public class JavadocParsingTest extends JavaParsingTestCase {
  public JavadocParsingTest() {
    super("parser-full/javadocParsing");
  }

  public void testJavadoc0() { doTest(true); }
  public void testJavadoc1() { doTest(true); }

  public void testTag0() { doTest(true); }
  public void testTag1() { doTest(true); }
  public void testTag2() { doTest(true); }
  public void testTag3() { doTest(true); }
  public void testTag4() { doTest(true); }
  public void testTag5() { doTest(true); }
  public void testTag6() { doTest(true); }

  public void testInlineTag0() { doTest(true); }
  public void testInlineTag1() { doTest(true); }
  public void testInlineTag2() { doTest(true); }
  public void testInlineTag3() { doTest(true); }

  public void testSeeTag0() { doTest(true); }
  public void testSeeTag1() { doTest(true); }
  public void testSeeTag2() { doTest(true); }
  public void testSeeTag3() { doTest(true); }
  public void testSeeTag4() { doTest(true); }
  public void testSeeTag5() { doTest(true); }
  public void testSeeTag6() { doTest(true); }
  public void testSeeTag7() { doTest(true); }
  public void testSeeTag8() { doTest(true); }
  public void testSeeTag9() { doTest(true); }
  public void testSeeTag10() { doTest(true); }
  public void testSeeTag11() { doTest(true); }
  public void testSeeTag12() { doTest(true); }
  public void testSeeTag13() { doTest(true); }
  public void testSeeTag14() { doTest(true); }
  public void testSeeTag15() { doTest(true); }
  public void testSeeTag16() { doTest(true); }

  public void testLinkTag0() { doTest(true); }
  public void testLinkTag1() { doTest(true); }
  public void testLinkTag2() { doTest(true); }
  public void testLinkTag3() { doTest(true); }
  public void testLinkTag4() { doTest(true); }
  public void testLinkTag5() { doTest(true); }
  public void testLinkTag6() { doTest(true); }

  public void testParamTag0() { doTest(true); }
  public void testParamTag1() { doTest(true); }

  public void testLinkPlainTag0() { doTest(true); }
  public void testLinkPlainTag1() { doTest(true); }
  public void testLinkPlainTag2() { doTest(true); }

  public void testException0() { doTest(true); }

  public void testSymbols01() { doTest(true); }
  public void testSymbols02() { doTest(true); }
  public void testSymbols03() { doTest(true); }
  public void testSymbols04() { doTest(true); }
  public void testSymbols05() { doTest(true); }

  public void testAdjacent01() { doTest(true); }
  public void testSeparated01() { doTest(true); }

  public void testTypeParam() { doTest(true); }
  public void testParameterlessTag() { doTest(true); }

  public void testCodeTag() { doTest(true); }
  public void testMultilineCodeTag() { doTest(true); }
  public void testCodeTagWithBraces() { doTest(true); }
  public void testLiteralTag() { doTest(true); }

  public void testIDEADEV_41403() { doTest(true); }

  public void testValueQualified() { doTest(true); }
  public void testValueUnqualifiedWithHash() { doTest(true); }
  public void testValueUnqualifiedWithoutHash() { doTest(true); }

  public void testThrowsTag() { doTest(true); }
  public void testUsesTag() { doTest(true); }
  public void testProvidesTag() { doTest(true); }
  public void testInlineTagIndex() { doTest(true); }
  public void testInlineTagSummary() { doTest(true); }

  public void testSnippetTag0() { doTest(true); }
  public void testSnippetTag1() { doTest(true); }
  public void testSnippetTag2() { doTest(true); }
  public void testSnippetTag3() { doTest(true); }
  public void testSnippetTag4() { doTest(true); }
  public void testSnippetTag5() { doTest(true); }
  public void testSnippetTag6() { doTest(true); }
  public void testSnippetTag7() { doTest(true); }
  public void testSnippetTag8() { doTest(true); }
  public void testSnippetTag9() { doTest(true); }
  public void testSnippetTag10() { doTest(true); }
  public void testSnippetTag11() { doTest(true); }
  public void testSnippetTag12() { doTest(true); }
}