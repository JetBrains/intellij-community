// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicReferenceParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicReferenceParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/references", configurator);
  }

  @Test
  public void testReference0() { doRefParserTest("a", false); }
  @Test
  public void testReference1() { doRefParserTest("a.", true); }
  @Test
  public void testReference2() { doRefParserTest("a.b", false); }

  @Test
  public void testType0() { doTypeParserTest("int"); }
  @Test
  public void testType1() { doTypeParserTest("a.b"); }
  @Test
  public void testType2() { doTypeParserTest("int[]"); }
  @Test
  public void testType3() { doTypeParserTest("int[]["); }
  @Test
  public void testType4() { doTypeParserTest("Map<String,List<String>>"); }
  @Test
  public void testType5() { doTypeParserTest("Object[]..."); }
  @Test
  public void testType6() { doTypeParserTest("@English String @NonEmpty []"); }
  @Test
  public void testType7() { doTypeParserTest("Diamond<>"); }
  @Test
  public void testType8() { doTypeParserTest("A|"); }
  @Test
  public void testType9() { doTypeParserTest("A|B"); }
  @Test
  public void testType10() { doTypeParserTest("Diamond<@TA>"); }

  @Test
  public void testTypeParams0() { doTypeParamsParserTest("<T>"); }
  @Test
  public void testTypeParams1() { doTypeParamsParserTest("<T, U>"); }
  @Test
  public void testTypeParams2() { doTypeParamsParserTest("<T"); }
  @Test
  public void testTypeParams3() { doTypeParamsParserTest("<T hack>"); }
  @Test
  public void testTypeParams4() { doTypeParamsParserTest("<T hack"); }
  @Test
  public void testTypeParams5() { doTypeParamsParserTest("<T extends X & Y<Z>>"); }
  @Test
  public void testTypeParams6() { doTypeParamsParserTest("<T supers X>"); }
  @Test
  public void testTypeParams7() { doTypeParamsParserTest("<T extends X, Y>"); }
  @Test
  public void testTypeParams8() { doTypeParamsParserTest("<?>"); }

  protected abstract void doRefParserTest(String text, boolean incomplete);

  protected abstract void doTypeParserTest(String text);

  protected abstract void doTypeParamsParserTest(String text);
}