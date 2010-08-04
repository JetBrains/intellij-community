
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class WhileParsingTest extends JavaParsingTestCase {
  public WhileParsingTest() {
    super("parser-full/statementParsing/while");
  }

  public void testNormal() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
}