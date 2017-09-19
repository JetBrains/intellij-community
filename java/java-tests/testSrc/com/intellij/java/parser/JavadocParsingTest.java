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
}