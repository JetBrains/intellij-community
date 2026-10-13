// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicPatternParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicPatternParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/patterns", configurator);
  }

  @Test
  public void testRecord0() { doParserTest("Point(int x, int y)"); }
  @Test
  public void testRecord1() { doParserTest("Point()"); }
  @Test
  public void testRecord2() { doParserTest("Box<Object>(String s)"); }
  @Test
  public void testRecord3() { doParserTest("NamedRecord(String s) name"); }
  @Test
  public void testRecord4() { doParserTest("Rec(,)"); }
  @Test
  public void testRecord5() { doParserTest("Rec(,,)"); }
  @Test
  public void testRecord6() { doParserTest("Rec(String s int i)"); }
  @Test
  public void testRecord7() { doParserTest("Rec(int a,) a"); }
  @Test
  public void testRecord8() { doParserTest("Rec(int a, "); }
  @Test
  public void testRecord9() { doParserTest("Rec(int a"); }
  @Test
  public void testRecord10() { doParserTest("Rec r"); }
  @Test
  public void testRecord11() { doParserTest("Rec(var x) r"); }
  @Test
  public void testRecord12() { doParserTest("Rec(String)"); }

  @Test
  public void testRecordUnnamed0() { doParserTest("Point(_)"); }
  @Test
  public void testRecordUnnamed1() { doParserTest("Point(int x, _)"); }
  @Test
  public void testRecordUnnamed2() { doParserTest("Point(_, int y)"); }
  @Test
  public void testRecordUnnamed3() { doParserTest("Point(_, _)"); }

  protected abstract void doParserTest(String text);
}
