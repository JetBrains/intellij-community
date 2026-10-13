// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicJavadocParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicJavadocParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/javadocParsing", configurator);
  }

  @Test
  public void testJavadoc0() { doTest(true); }
  @Test
  public void testJavadoc1() { doTest(true); }

  @Test
  public void testTag0() { doTest(true); }
  @Test
  public void testTag1() { doTest(true); }
  @Test
  public void testTag2() { doTest(true); }
  @Test
  public void testTag3() { doTest(true); }
  @Test
  public void testTag4() { doTest(true); }
  @Test
  public void testTag5() { doTest(true); }
  @Test
  public void testTag6() { doTest(true); }

  @Test
  public void testInheritDocTag0() { doTest(true); }

  @Test
  public void testInlineTag0() { doTest(true); }
  @Test
  public void testInlineTag1() { doTest(true); }
  @Test
  public void testInlineTag2() { doTest(true); }
  @Test
  public void testInlineTag3() { doTest(true); }

  @Test
  public void testSeeTag0() { doTest(true); }
  @Test
  public void testSeeTag1() { doTest(true); }
  @Test
  public void testSeeTag2() { doTest(true); }
  @Test
  public void testSeeTag3() { doTest(true); }
  @Test
  public void testSeeTag4() { doTest(true); }
  @Test
  public void testSeeTag5() { doTest(true); }
  @Test
  public void testSeeTag6() { doTest(true); }
  @Test
  public void testSeeTag7() { doTest(true); }
  @Test
  public void testSeeTag8() { doTest(true); }
  @Test
  public void testSeeTag9() { doTest(true); }
  @Test
  public void testSeeTag10() { doTest(true); }
  @Test
  public void testSeeTag11() { doTest(true); }
  @Test
  public void testSeeTag12() { doTest(true); }
  @Test
  public void testSeeTag13() { doTest(true); }
  @Test
  public void testSeeTag14() { doTest(true); }
  @Test
  public void testSeeTag15() { doTest(true); }
  @Test
  public void testSeeTag16() { doTest(true); }
  @Test
  public void testSeeTag17() { doTest(true); }
  @Test
  public void testSeeTag18() { doTest(true); }
  @Test
  public void testSeeTag19() { doTest(true); }
  @Test
  public void testSeeTag20() { doTest(true); }
  @Test
  public void testSeeTag21() { doTest(true); }

  @Test
  public void testLinkTag0() { doTest(true); }
  @Test
  public void testLinkTag1() { doTest(true); }
  @Test
  public void testLinkTag2() { doTest(true); }
  @Test
  public void testLinkTag3() { doTest(true); }
  @Test
  public void testLinkTag4() { doTest(true); }
  @Test
  public void testLinkTag5() { doTest(true); }
  @Test
  public void testLinkTag6() { doTest(true); }
  @Test
  public void testLinkTag7() { doTest(true); }
  @Test
  public void testLinkTag8() { doTest(true); }
  @Test
  public void testLinkTag9() { doTest(true); }
  @Test
  public void testLinkTag10() { doTest(true); }
  @Test
  public void testLinkTag11() { doTest(true); }

  @Test
  public void testParamTag0() { doTest(true); }
  @Test
  public void testParamTag1() { doTest(true); }

  @Test
  public void testLinkPlainTag0() { doTest(true); }
  @Test
  public void testLinkPlainTag1() { doTest(true); }
  @Test
  public void testLinkPlainTag2() { doTest(true); }

  @Test
  public void testException0() { doTest(true); }

  @Test
  public void testSymbols01() { doTest(true); }
  @Test
  public void testSymbols02() { doTest(true); }
  @Test
  public void testSymbols03() { doTest(true); }
  @Test
  public void testSymbols04() { doTest(true); }
  @Test
  public void testSymbols05() { doTest(true); }

  @Test
  public void testAdjacent01() { doTest(true); }
  @Test
  public void testSeparated01() { doTest(true); }

  @Test
  public void testTypeParam() { doTest(true); }
  @Test
  public void testParameterlessTag() { doTest(true); }

  @Test
  public void testCodeTag() { doTest(true); }
  @Test
  public void testMultilineCodeTag() { doTest(true); }
  @Test
  public void testCodeTagWithBraces() { doTest(true); }
  @Test
  public void testLiteralTag() { doTest(true); }

  @Test
  public void testIDEADEV_41403() { doTest(true); }

  @Test
  public void testValueQualified() { doTest(true); }
  @Test
  public void testValueQualifiedBreak() { doTest(true); }
  @Test
  public void testValueUnqualifiedWithHash() { doTest(true); }
  @Test
  public void testValueUnqualifiedWithoutHash() { doTest(true); }
  @Test
  public void testValueWithFormat() { doTest(true); }

  @Test
  public void testThrowsTag() { doTest(true); }
  @Test
  public void testUsesTag() { doTest(true); }
  @Test
  public void testProvidesTag() { doTest(true); }
  @Test
  public void testInlineTagIndex() { doTest(true); }
  @Test
  public void testInlineTagSummary() { doTest(true); }

  @Test
  public void testSnippetTag0() { doTest(true); }
  @Test
  public void testSnippetTag1() { doTest(true); }
  @Test
  public void testSnippetTag2() { doTest(true); }
  @Test
  public void testSnippetTag3() { doTest(true); }
  @Test
  public void testSnippetTag4() { doTest(true); }
  @Test
  public void testSnippetTag5() { doTest(true); }
  @Test
  public void testSnippetTag6() { doTest(true); }
  @Test
  public void testSnippetTag7() { doTest(true); }
  @Test
  public void testSnippetTag8() { doTest(true); }
  @Test
  public void testSnippetTag9() { doTest(true); }
  @Test
  public void testSnippetTag10() { doTest(true); }
  @Test
  public void testSnippetTag11() { doTest(true); }
  @Test
  public void testSnippetTag12() { doTest(true); }

  @Test
  public void testNestedTag0() { doTest(true); }
  @Test
  public void testNestedTag1() { doTest(true); }

  // Markdown version, to ensure parity
  @Test
  public void testAdjacent01Markdown() { doTest(true); }

  @Test
  public void testCodeTagMarkdown() { doTest(true); }
  @Test
  public void testCodeTagWithBracesMarkdown() { doTest(true); }

  @Test
  public void testException0Markdown() { doTest(true); }
  @Test
  public void testIDEADEV_41403Markdown() { doTest(true); }

  @Test
  public void testInlineTag0Markdown() { doTest(true); }
  @Test
  public void testInlineTag1Markdown() { doTest(true); }
  @Test
  public void testInlineTag2Markdown() { doTest(true); }
  @Test
  public void testInlineTag3Markdown() { doTest(true); }
  @Test
  public void testInlineTagIndexMarkdown() { doTest(true); }
  @Test
  public void testInlineTagSummaryMarkdown() { doTest(true); }

  @Test
  public void testJavadoc0Markdown() { doTest(true); }
  @Test
  public void testJavadoc1Markdown() { doTest(true); }

  @Test
  public void testLinkPlainTag0Markdown() { doTest(true); }
  @Test
  public void testLinkPlainTag1Markdown() { doTest(true); }
  @Test
  public void testLinkPlainTag2Markdown() { doTest(true); }

  @Test
  public void testLinkTag0Markdown() { doTest(true); }
  @Test
  public void testLinkTag1Markdown() { doTest(true); }
  @Test
  public void testLinkTag2Markdown() { doTest(true); }
  @Test
  public void testLinkTag3Markdown() { doTest(true); }
  @Test
  public void testLinkTag4Markdown() { doTest(true); }
  @Test
  public void testLinkTag5Markdown() { doTest(true); }
  @Test
  public void testLinkTag6Markdown() { doTest(true); }
  @Test
  public void testLinkTag9Markdown() { doTest(true); }
  @Test
  public void testLinkTag10Markdown() { doTest(true); }

  @Test
  public void testLiteralTagMarkdown() { doTest(true); }
  @Test
  public void testMultilineCodeTagMarkdown() { doTest(true); }
  @Test
  public void testParamTag0Markdown() { doTest(true); }
  @Test
  public void testParamTag1Markdown() { doTest(true); }
  @Test
  public void testParameterlessTagMarkdown() { doTest(true); }
  @Test
  public void testProvidesTagMarkdown() { doTest(true); }

  @Test
  public void testSeeTag0Markdown() { doTest(true); }
  @Test
  public void testSeeTag1Markdown() { doTest(true); }
  @Test
  public void testSeeTag2Markdown() { doTest(true); }
  @Test
  public void testSeeTag3Markdown() { doTest(true); }
  @Test
  public void testSeeTag4Markdown() { doTest(true); }
  @Test
  public void testSeeTag5Markdown() { doTest(true); }
  @Test
  public void testSeeTag6Markdown() { doTest(true); }
  @Test
  public void testSeeTag7Markdown() { doTest(true); }
  @Test
  public void testSeeTag8Markdown() { doTest(true); }
  @Test
  public void testSeeTag9Markdown() { doTest(true); }
  @Test
  public void testSeeTag10Markdown() { doTest(true); }
  @Test
  public void testSeeTag11Markdown() { doTest(true); }
  @Test
  public void testSeeTag12Markdown() { doTest(true); }
  @Test
  public void testSeeTag13Markdown() { doTest(true); }
  @Test
  public void testSeeTag14Markdown() { doTest(true); }
  @Test
  public void testSeeTag15Markdown() { doTest(true); }
  @Test
  public void testSeeTag16Markdown() { doTest(true); }
  @Test
  public void testSeeTag19Markdown() { doTest(true); }
  @Test
  public void testSeeTag20Markdown() { doTest(true); }
  @Test
  public void testSeeTag21Markdown() { doTest(true); }
  @Test
  public void testSeeTag22Markdown() { doTest(true); }

  @Test
  public void testSeparated01Markdown() { doTest(true); }

  @Test
  public void testSnippetTag0Markdown() { doTest(true); }
  @Test
  public void testSnippetTag1Markdown() { doTest(true); }
  @Test
  public void testSnippetTag2Markdown() { doTest(true); }
  @Test
  public void testSnippetTag3Markdown() { doTest(true); }
  @Test
  public void testSnippetTag4Markdown() { doTest(true); }
  @Test
  public void testSnippetTag5Markdown() { doTest(true); }
  @Test
  public void testSnippetTag6Markdown() { doTest(true); }
  @Test
  public void testSnippetTag7Markdown() { doTest(true); }
  @Test
  public void testSnippetTag8Markdown() { doTest(true); }
  @Test
  public void testSnippetTag9Markdown() { doTest(true); }
  @Test
  public void testSnippetTag10Markdown() { doTest(true); }
  @Test
  public void testSnippetTag11Markdown() { doTest(true); }
  @Test
  public void testSnippetTag12Markdown() { doTest(true); }

  @Test
  public void testSymbols01Markdown() { doTest(true); }
  @Test
  public void testSymbols02Markdown() { doTest(true); }
  @Test
  public void testSymbols03Markdown() { doTest(true); }
  @Test
  public void testSymbols04Markdown() { doTest(true); }
  @Test
  public void testSymbols05Markdown() { doTest(true); }

  @Test
  public void testTag0Markdown() { doTest(true); }
  @Test
  public void testTag1Markdown() { doTest(true); }
  @Test
  public void testTag2Markdown() { doTest(true); }
  @Test
  public void testTag3Markdown() { doTest(true); }
  @Test
  public void testTag4Markdown() { doTest(true); }
  @Test
  public void testTag5Markdown() { doTest(true); }
  @Test
  public void testTag6Markdown() { doTest(true); }

  @Test
  public void testThrowsTagMarkdown() { doTest(true); }
  @Test
  public void testTypeParamMarkdown() { doTest(true); }
  @Test
  public void testUsesTagMarkdown() { doTest(true); }
  @Test
  public void testValueQualifiedMarkdown() { doTest(true); }
  @Test
  public void testValueUnqualifiedWithHashMarkdown() { doTest(true); }
  @Test
  public void testValueUnqualifiedWithoutHashMarkdown() { doTest(true); }

  @Test
  public void testCodeBlockMarkdown01() { doTest(true); }
  @Test
  public void testCodeBlockMarkdown02() { doTest(true); }
  @Test
  public void testCodeBlockMarkdown03() { doTest(true); }
  @Test
  public void testCodeBlockMarkdown04() { doTest(true); }
  @Test
  public void testCodeBlockMarkdown05() { doTest(true); }

  @Test
  public void testReferenceLinkMarkdown00() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown01() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown02() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown03() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown04() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown05() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown06() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown07() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown08() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown09() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown10() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown11() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown12() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown13() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown14() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown15() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown16() { doTest(true); }
  @Test
  public void testReferenceLinkMarkdown17() { doTest(true); }

  @Test
  public void testNestedTag0Markdown() { doTest(true); }
  @Test
  public void testNestedTag1Markdown() { doTest(true); }
  @Test
  public void testNestedTag2Markdown() { doTest(true); }
  @Test
  public void testGenerics() { doTest(true); }
  
  @Test
  public void testEscapedCharsMarkdown() { doTest(true); }

  @Test
  public void testMarkdownWithDocCommentBeforeMarkdown() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest(true);
  }

  @Test
  public void testOnlyMarkdownBeforeMarkdown() {
    setLanguageLevel(LanguageLevel.JDK_21);
    doTest(true);
  }

  @Test
  public void testMarkdownWithDocCommentAfterMarkdown() {
    setLanguageLevel(LanguageLevel.JDK_23);
    doTest(true);
  }

  @Test
  public void testOnlyMarkdownAfterMarkdown() {
    setLanguageLevel(LanguageLevel.JDK_23);
    doTest(true);
  }

  @Test
  public void testNoValueElementTagsMarkdown() { doTest(true); }

  @Test
  public void testNoAsterisks() { doTest(true); }
}