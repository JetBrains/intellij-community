
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class DeclarationsWithGenericsParsingTest extends JavaParsingTestCase {
  public DeclarationsWithGenericsParsingTest() {
    super("parser-full/statementParsing/genericsParsing");
  }

  public void testLocalVar() { doTest(true); }
  public void testFor() { doTest(true); }
}