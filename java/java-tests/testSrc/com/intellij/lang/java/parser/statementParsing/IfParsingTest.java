
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class IfParsingTest extends JavaParsingTestCase {
  public IfParsingTest() {
    super("parser-full/statementParsing/if");
  }

  public void testNormalWithElse() { doTest(true); }
  public void testNormalNoElse() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
  public void testUncomplete7() { doTest(true); }
}