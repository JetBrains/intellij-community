// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicReferenceParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicReferenceParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/references", configurator);
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

  protected abstract void doRefParserTest(String text, boolean incomplete);

  protected abstract void doTypeParserTest(String text);

  protected abstract void doTypeParamsParserTest(String text);
}