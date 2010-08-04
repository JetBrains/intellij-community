
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class ReturnParsingTest extends JavaParsingTestCase {
  public ReturnParsingTest() {
    super("parser-full/statementParsing/return");
  }

  public void testNormalNoResult() { doTest(true); }
  public void testNormalWithResult() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
}