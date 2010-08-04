package com.intellij.lang.java.parser.annotationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


/**
 * @author ven
 */
public class AnnotationDeclarationParsingTest extends JavaParsingTestCase {
  public AnnotationDeclarationParsingTest() {
    super("parser-full/annotationParsing/declaration");
  }

  public void testSimple() { doTest(true); }

  public void testDefault() { doTest(true); }

  public void testNested() { doTest(true); }

  public void testInner() { doTest(true); }

  public void testOtherMembers() { doTest(true); }
}
