
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class ThrowParsingTest extends JavaParsingTestCase {
  public ThrowParsingTest() {
    super("parser-full/statementParsing/throw");
  }

  public void testNormal() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
}