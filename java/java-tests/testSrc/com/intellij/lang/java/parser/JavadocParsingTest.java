/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

public class JavadocParsingTest extends JavaParsingTestCase {
  public JavadocParsingTest() {
    super("parser-full/javadocParsing");
  }

  public void testJavadoc0() throws Exception { doTest(true); }
  public void testTag0() throws Exception { doTest(true); }
  public void testTag1() throws Exception { doTest(true); }
  public void testTag2() throws Exception { doTest(true); }
  public void testTag3() throws Exception { doTest(true); }
  public void testTag4() throws Exception { doTest(true); }
  public void testTag5() throws Exception { doTest(true); }
  public void testInlineTag0() throws Exception { doTest(true); }
  public void testInlineTag1() throws Exception { doTest(true); }
  public void testInlineTag2() throws Exception { doTest(true); }
  public void testInlineTag3() throws Exception { doTest(true); }

  public void testSeeTag0() throws Exception { doTest(true); }
  public void testSeeTag1() throws Exception { doTest(true); }
  public void testSeeTag2() throws Exception { doTest(true); }
  public void testSeeTag3() throws Exception { doTest(true); }
  public void testSeeTag4() throws Exception { doTest(true); }
  public void testSeeTag5() throws Exception { doTest(true); }
  public void testSeeTag6() throws Exception { doTest(true); }
  public void testSeeTag7() throws Exception { doTest(true); }
  public void testSeeTag8() throws Exception { doTest(true); }
  public void testSeeTag9() throws Exception { doTest(true); }
  public void testSeeTag10() throws Exception { doTest(true); }
  public void testSeeTag11() throws Exception { doTest(true); }
  public void testSeeTag12() throws Exception { doTest(true); }
  public void testSeeTag13() throws Exception { doTest(true); }
  public void testSeeTag14() throws Exception { doTest(true); }
  public void testSeeTag15() throws Exception { doTest(true); }
  public void testSeeTag16() throws Exception { doTest(true); }

  public void testLinkTag0() throws Exception { doTest(true); }
  public void testLinkTag1() throws Exception { doTest(true); }
  public void testLinkTag2() throws Exception { doTest(true); }
  public void testLinkTag3() throws Exception { doTest(true); }
  public void testLinkTag4() throws Exception { doTest(true); }
  public void testLinkTag5() throws Exception { doTest(true); }
  public void testLinkTag6() throws Exception { doTest(true); }

  public void testParamTag1() throws Exception { doTest(true); }

  public void testLinkPlainTag0() throws Exception { doTest(true); }
  public void testLinkPlainTag1() throws Exception { doTest(true); }
  public void testLinkPlainTag2() throws Exception { doTest(true); }

  public void testException0() throws Exception { doTest(true); }

  public void testSymbols01() throws Exception { doTest(true); }
  public void testSymbols02() throws Exception { doTest(true); }
  public void testSymbols03() throws Exception { doTest(true); }
  public void testSymbols04() throws Exception { doTest(true); }
  public void testSymbols05() throws Exception { doTest(true); }

  public void testAdjacent01() throws Exception { doTest(true); }
  public void testSeparated01() throws Exception { doTest(true); }

  public void testTypeParam() throws Exception { doTest(true); }
  public void testParameterlessTag() throws Exception { doTest(true); }
  
  public void testIDEADEV_41403() throws Exception {doTest(true);}
}
