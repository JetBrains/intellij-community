
package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class CodeBlockParsingTest extends JavaParsingTestCase {
  public CodeBlockParsingTest() {
    super("parser-full/statementParsing/codeBlock");
  }

  public void testSimple() { doTest(true); }
  public void testAnonymousInSmartCompletion() throws Throwable { doTest(true); }
}