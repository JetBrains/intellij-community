
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class ForParsingTest extends JavaParsingTestCase {
  public ForParsingTest() {
    super("parser-full/statementParsing/for");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
  public void testForEach1() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
  public void testUncomplete7() { doTest(true); }
  public void testUncomplete8() { doTest(true); }
  public void testUncomplete9() { doTest(true); }
  public void testUncomplete10() { doTest(true); }
}