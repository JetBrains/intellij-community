
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class AssertParsingTest extends JavaParsingTestCase {
  public AssertParsingTest() {
    super("parser-full/statementParsing/assert");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
}