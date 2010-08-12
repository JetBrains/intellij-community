
package com.intellij.lang.java.parser.declarationParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;


public class FieldParsingTest extends JavaParsingTestCase {
  public FieldParsingTest() {
    super("parser-full/declarationParsing/field");
  }

  public void testSimple() { doTest(true); }
  public void testMulti() { doTest(true); }

  public void testUnclosedBracket() { doTest(true); }
  public void testMissingInitializer() { doTest(true); }
  public void testUnclosedComma() { doTest(true); }
  public void testUnclosedSemicolon() { doTest(true); }
  public void testMissingInitializerExpression() { doTest(true); }

  public void testMultiLineUnclosed0() { doTest(true); }
  public void testMultiLineUnclosed1() { doTest(true); }

  public void testComplexInitializer() { doTest(true); }

  public void testErrors() { doTest(true); }
}