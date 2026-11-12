// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplaceFormatStringWithConcatenationIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceFormatStringWithConcatenationIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testNotSupported() {
    ipp.get().doTestIntentionNotAvailable("class C {" +
                                "  String m() {" +
                                "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%d\", 1);" +
                                "  }" +
                                "}");
  }

  @Test
  public void testSimple() {
    ipp.get().doTest("class C {" +
           "  String m() {" +
           "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%s\", 1);" +
           "  }" +
           "}",

           "class C {" +
           "  String m() {" +
           "    return String.valueOf(1);" +
           "  }" +
           "}");
  }

  @Test
  public void testMultiple() {
    ipp.get().doTest("class C {" +
           "  String m(String expected, String actual) {" +
           "    return String./*_Replace 'String.format()' with concatenation*/format(\"Expected to get a '%s', got a '%s' instead\", expected, actual);" +
           "  }" +
           "}",

           "class C {" +
           "  String m(String expected, String actual) {" +
           "    return \"Expected to get a '\" + expected + \"', got a '\" + actual + \"' instead\";" +
           "  }" +
           "}");
  }

  @Test
  public void testMultipleWithNothingInBetween() {
    ipp.get().doTest("class X {" +
           "  String m(String tempDataFolderPath, String fileName) {" +
           "    //noinspection UnnecessaryCallToStringValueOf\n" +
           "    return String.format(\"%s/f%s%s\", /*_Replace 'String.format()' with concatenation*/tempDataFolderPath, Double.toString(Math.random()), fileName);" +
           "  }" +
           "}",

           "class X {" +
           "  String m(String tempDataFolderPath, String fileName) {" +
           "    //noinspection UnnecessaryCallToStringValueOf\n" +
           "    return tempDataFolderPath + \"/f\" + Double.toString(Math.random()) + fileName;" +
           "  }" +
           "}");
  }

  @Test
  public void testPercentAtTheEnd() {
    ipp.get().doTestIntentionNotAvailable("class X {" +
           "  String x() {" +
           "    //noinspection ResultOfMethodCallIgnored\n" +
           "    String.format(/*_Replace 'String.format()' with concatenation*/\"nope%\", 1);" +
           "  }" +
           "}");
  }

  @Test
  public void testNewline() {
    ipp.get().doTest("class Z {" +
           "  String m(boolean b) {" +
           "    return String.format/*_Replace 'String.format()' with concatenation*/(\"b:\\n %s\", b);" +
           "  }" +
           "}",

           "class Z {" +
           "  String m(boolean b) {" +
           "    return \"b:\\n \" + b;" +
           "  }" +
           "}");
  }

  @Test
  public void testSingleCharacterSuffix() {
    ipp.get().doTest("class Idempotent {" +
           "  String x() {" +
           "    return String./*_Replace 'String.format()' with concatenation*/format(\"%s.\", \"foo\");" +
           "  }" +
           "}",

           "class Idempotent {" +
           "  String x() {" +
           "    return \"foo\" + \".\";" +
           "  }" +
           "}");
  }

  @Test
  public void testPsiStructure() {
    ipp.get().doTest("/** @noinspection ClassInitializerMayBeStatic*/" +
           "class PsiStructure{{" +
           "  String s = \"TEST_DATA_PATH\" + /*_Replace 'String.format()' with concatenation*/String.format(\"templates/MyTemplate.%s.html\", 1);" +
           "}}",

           "/** @noinspection ClassInitializerMayBeStatic*/" +
           "class PsiStructure{{" +
           "  String s = \"TEST_DATA_PATH\" + \"templates/MyTemplate.\" + 1 + \".html\";" +
           "}}");
  }

  @Test
  public void testConditional() {
    ipp.get().doTest("""
             class Info {

                 public String name;
                 public boolean isSource;

                 public boolean isSource() {
                     return isSource;
                 }

                 public String getName() {
                     return name;
                 }

                 @Override
                 public final String toString() {
                     return String.format("%s%s port", getName(),
                                          isSource() ? " source" : /*_Replace 'String.format()' with concatenation*/" target");
                 }
             }""",

           """
             class Info {

                 public String name;
                 public boolean isSource;

                 public boolean isSource() {
                     return isSource;
                 }

                 public String getName() {
                     return name;
                 }

                 @Override
                 public final String toString() {
                     return getName() + (isSource() ? " source" : " target") + " port";
                 }
             }""");
  }
}