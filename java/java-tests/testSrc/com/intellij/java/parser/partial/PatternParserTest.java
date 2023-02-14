// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.PatternParser;

public class PatternParserTest extends JavaParsingTestCase {
  public PatternParserTest() {
    super("parser-partial/patterns");
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

  private void doParserTest(String text) {
    doParserTest(text, builder -> {
      PatternParser parser = JavaParser.INSTANCE.getPatternParser();
      if (!parser.isPattern(builder)) {
        throw new IllegalArgumentException("Pattern is not expected");
      }
      parser.parsePattern(builder);
    });
  }
}
