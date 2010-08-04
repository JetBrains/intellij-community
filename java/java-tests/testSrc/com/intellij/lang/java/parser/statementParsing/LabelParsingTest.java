
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class LabelParsingTest extends JavaParsingTestCase {
  public LabelParsingTest() {
    super("parser-full/statementParsing/label");
  }

  public void testSimple() { doTest(true); }
}