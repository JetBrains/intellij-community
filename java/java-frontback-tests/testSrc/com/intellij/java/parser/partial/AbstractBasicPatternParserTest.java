// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicPatternParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicPatternParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/patterns", configurator);
  }

  public void testRecord0() { doParserTest("Point(int x, int y)"); }
  public void testRecord1() { doParserTest("Point()"); }
  public void testRecord2() { doParserTest("Box<Object>(String s)"); }
  public void testRecord3() { doParserTest("NamedRecord(String s) name"); }
  public void testRecord4() { doParserTest("Rec(,)"); }
  public void testRecord5() { doParserTest("Rec(,,)"); }
  public void testRecord6() { doParserTest("Rec(String s int i)"); }
  public void testRecord7() { doParserTest("Rec(int a,) a"); }
  public void testRecord8() { doParserTest("Rec(int a, "); }
  public void testRecord9() { doParserTest("Rec(int a"); }
  public void testRecord10() { doParserTest("Rec r"); }
  public void testRecord11() { doParserTest("Rec(var x) r"); }
  public void testRecord12() { doParserTest("Rec(String)"); }

  public void testRecordUnnamed0() { doParserTest("Point(_)"); }
  public void testRecordUnnamed1() { doParserTest("Point(int x, _)"); }
  public void testRecordUnnamed2() { doParserTest("Point(_, int y)"); }
  public void testRecordUnnamed3() { doParserTest("Point(_, _)"); }

  protected abstract void doParserTest(String text);
}
