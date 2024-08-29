// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavadocParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicJavadocParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/javadocParsing", configurator);
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

  // Markdown version, to ensure parity
  public void testAdjacent01Markdown() { doTest(true); }

  public void testCodeTagMarkdown() { doTest(true); }
  public void testCodeTagWithBracesMarkdown() { doTest(true); }

  public void testException0Markdown() { doTest(true); }
  public void testIDEADEV_41403Markdown() { doTest(true); }

  public void testInlineTag0Markdown() { doTest(true); }
  public void testInlineTag1Markdown() { doTest(true); }
  public void testInlineTag2Markdown() { doTest(true); }
  public void testInlineTag3Markdown() { doTest(true); }
  public void testInlineTagIndexMarkdown() { doTest(true); }
  public void testInlineTagSummaryMarkdown() { doTest(true); }

  public void testJavadoc0Markdown() { doTest(true); }
  public void testJavadoc1Markdown() { doTest(true); }

  public void testLinkPlainTag0Markdown() { doTest(true); }
  public void testLinkPlainTag1Markdown() { doTest(true); }
  public void testLinkPlainTag2Markdown() { doTest(true); }

  public void testLinkTag0Markdown() { doTest(true); }
  public void testLinkTag1Markdown() { doTest(true); }
  public void testLinkTag2Markdown() { doTest(true); }
  public void testLinkTag3Markdown() { doTest(true); }
  public void testLinkTag4Markdown() { doTest(true); }
  public void testLinkTag5Markdown() { doTest(true); }
  public void testLinkTag6Markdown() { doTest(true); }

  public void testLiteralTagMarkdown() { doTest(true); }
  public void testMultilineCodeTagMarkdown() { doTest(true); }
  public void testParamTag0Markdown() { doTest(true); }
  public void testParamTag1Markdown() { doTest(true); }
  public void testParameterlessTagMarkdown() { doTest(true); }
  public void testProvidesTagMarkdown() { doTest(true); }

  public void testSeeTag0Markdown() { doTest(true); }
  public void testSeeTag1Markdown() { doTest(true); }
  public void testSeeTag2Markdown() { doTest(true); }
  public void testSeeTag3Markdown() { doTest(true); }
  public void testSeeTag4Markdown() { doTest(true); }
  public void testSeeTag5Markdown() { doTest(true); }
  public void testSeeTag6Markdown() { doTest(true); }
  public void testSeeTag7Markdown() { doTest(true); }
  public void testSeeTag8Markdown() { doTest(true); }
  public void testSeeTag9Markdown() { doTest(true); }
  public void testSeeTag10Markdown() { doTest(true); }
  public void testSeeTag11Markdown() { doTest(true); }
  public void testSeeTag12Markdown() { doTest(true); }
  public void testSeeTag13Markdown() { doTest(true); }
  public void testSeeTag14Markdown() { doTest(true); }
  public void testSeeTag15Markdown() { doTest(true); }
  public void testSeeTag16Markdown() { doTest(true); }

  public void testSeparated01Markdown() { doTest(true); }

  public void testSnippetTag0Markdown() { doTest(true); }
  public void testSnippetTag1Markdown() { doTest(true); }
  public void testSnippetTag2Markdown() { doTest(true); }
  public void testSnippetTag3Markdown() { doTest(true); }
  public void testSnippetTag4Markdown() { doTest(true); }
  public void testSnippetTag5Markdown() { doTest(true); }
  public void testSnippetTag6Markdown() { doTest(true); }
  public void testSnippetTag7Markdown() { doTest(true); }
  public void testSnippetTag8Markdown() { doTest(true); }
  public void testSnippetTag9Markdown() { doTest(true); }
  public void testSnippetTag10Markdown() { doTest(true); }
  public void testSnippetTag11Markdown() { doTest(true); }
  public void testSnippetTag12Markdown() { doTest(true); }

  public void testSymbols01Markdown() { doTest(true); }
  public void testSymbols02Markdown() { doTest(true); }
  public void testSymbols03Markdown() { doTest(true); }
  public void testSymbols04Markdown() { doTest(true); }
  public void testSymbols05Markdown() { doTest(true); }

  public void testTag0Markdown() { doTest(true); }
  public void testTag1Markdown() { doTest(true); }
  public void testTag2Markdown() { doTest(true); }
  public void testTag3Markdown() { doTest(true); }
  public void testTag4Markdown() { doTest(true); }
  public void testTag5Markdown() { doTest(true); }
  public void testTag6Markdown() { doTest(true); }

  public void testThrowsTagMarkdown() { doTest(true); }
  public void testTypeParamMarkdown() { doTest(true); }
  public void testUsesTagMarkdown() { doTest(true); }
  public void testValueQualifiedMarkdown() { doTest(true); }
  public void testValueUnqualifiedWithHashMarkdown() { doTest(true); }
  public void testValueUnqualifiedWithoutHashMarkdown() { doTest(true); }

  public void testCodeBlockMarkdown01() { doTest(true); }
  public void testCodeBlockMarkdown02() { doTest(true); }
  public void testCodeBlockMarkdown03() { doTest(true); }

  public void testReferenceLinkMarkdown00() { doTest(true); }
  public void testReferenceLinkMarkdown01() { doTest(true); }
  public void testReferenceLinkMarkdown02() { doTest(true); }
  public void testReferenceLinkMarkdown03() { doTest(true); }
  public void testReferenceLinkMarkdown04() { doTest(true); }
  public void testReferenceLinkMarkdown05() { doTest(true); }
  public void testReferenceLinkMarkdown06() { doTest(true); }
  public void testReferenceLinkMarkdown07() { doTest(true); }
  public void testReferenceLinkMarkdown08() { doTest(true); }
  public void testReferenceLinkMarkdown09() { doTest(true); }
  public void testReferenceLinkMarkdown10() { doTest(true); }
  public void testReferenceLinkMarkdown11() { doTest(true); }

}