package com.intellij.lang.java.parser.statementParsing;

import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.pom.java.LanguageLevel;


public class TryParsingTest extends JavaParsingTestCase {
  public TryParsingTest() {
    super("parser-full/statementParsing/try");
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
  public void testNormal3() { doTest(true); }
  public void testNormal4() {
    withLevel(LanguageLevel.JDK_1_7, new Runnable() { @Override public void run() {
      doTest(true);
    }});
  }

  public void testIncomplete1() { doTest(true); }
  public void testIncomplete2() { doTest(true); }
  public void testIncomplete3() { doTest(true); }
  public void testIncomplete4() { doTest(true); }
  public void testIncomplete5() { doTest(true); }
  public void testIncomplete6() { doTest(true); }
  public void testIncomplete7() { doTest(true); }
  public void testIncomplete8() { doTest(true); }
  public void testIncomplete9() {
    withLevel(LanguageLevel.JDK_1_7, new Runnable() { @Override public void run() {
      doTest(true);
    }});
  }
}