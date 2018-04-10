// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.ReferenceParser;

public class ReferenceParserTest extends JavaParsingTestCase {
  public ReferenceParserTest() {
    super("parser-partial/references");
  }

  public void testReference0() { doRefParserTest("a", false); }
  public void testReference1() { doRefParserTest("a.", true); }
  public void testReference2() { doRefParserTest("a.b", false); }

  public void testType0() { doTypeParserTest("int"); }
  public void testType1() { doTypeParserTest("a.b"); }
  public void testType2() { doTypeParserTest("int[]"); }
  public void testType3() { doTypeParserTest("int[]["); }
  public void testType4() { doTypeParserTest("Map<String,List<String>>"); }
  public void testType5() { doTypeParserTest("Object[]..."); }
  public void testType6() { doTypeParserTest("@English String @NonEmpty []"); }
  public void testType7() { doTypeParserTest("Diamond<>"); }
  public void testType8() { doTypeParserTest("A|"); }
  public void testType9() { doTypeParserTest("A|B"); }
  public void testType10() { doTypeParserTest("Diamond<@TA>"); }

  public void testTypeParams0() { doTypeParamsParserTest("<T>"); }
  public void testTypeParams1() { doTypeParamsParserTest("<T, U>"); }
  public void testTypeParams2() { doTypeParamsParserTest("<T"); }
  public void testTypeParams3() { doTypeParamsParserTest("<T hack>"); }
  public void testTypeParams4() { doTypeParamsParserTest("<T hack"); }
  public void testTypeParams5() { doTypeParamsParserTest("<T extends X & Y<Z>>"); }
  public void testTypeParams6() { doTypeParamsParserTest("<T supers X>"); }
  public void testTypeParams7() { doTypeParamsParserTest("<T extends X, Y>"); }
  public void testTypeParams8() { doTypeParamsParserTest("<?>"); }

  public void testAnyTypeParams() { doTypeParamsParserTest("<any T>"); }
  public void testAnyTypeArgs() { doTypeParserTest("T<E_SRC, any, E_DST, ?>"); }

  private void doRefParserTest(String text, boolean incomplete) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, incomplete, false, false, false));
  }

  private void doTypeParserTest(String text) {
    int flags = ReferenceParser.ELLIPSIS | ReferenceParser.DIAMONDS | ReferenceParser.DISJUNCTIONS;
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags));
  }

  private void doTypeParamsParserTest(String text) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseTypeParameters(builder));
  }
}