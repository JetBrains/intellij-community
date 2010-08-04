
package com.intellij.lang.java.parser.declarationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class CommentBindingTest extends JavaParsingTestCase {
  public CommentBindingTest() {
    super("parser-full/declarationParsing/commentBinding");
  }

  public void testBindBefore1() { doTest(true); }
  public void testBindBefore2() { doTest(true); }
  public void testBindBefore3() { doTest(true); }
  public void testBindBefore3a() { doTest(true); }
  public void testBindBefore4() { doTest(true); }
}