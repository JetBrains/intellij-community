
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class AssignmentParsingTest extends JavaParsingTestCase {
  public AssignmentParsingTest() {
    super("parser-full/statementParsing/assignment");
  }

  public void testSimple() { doTest(true); }
}