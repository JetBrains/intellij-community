// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.StringTemplateReverseMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @see StringTemplateReverseMigrationInspection
 */
public class StringTemplateReverseMigrationInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testJava23() {
    IdeaTestUtil.withLevel(
      getModule(),
      LanguageLevel.JDK_23,
      () -> doTest("""
                         class X {{
                           String s = STR."<caret>xyz\\{1+2}";
                         }}
                     """, """
                         class X {{
                           String s = "xyz" + (1 + 2);
                         }}
                     """));
  }
  
  public void testSimple() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String name = "World";
                 String test = STR."<caret>Hello \\{name}!!!";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String name = "World";
                 String test = "Hello " + name + "!!!";
               }
             }""");
  }
  
  public void testTrailingWhitespace() {
    doTest("""
             class TrailingWhitespace {
               void foo(String name) {
                   String s = STR.""\"<caret>
                                        Hello \\{name}
                                        ""\";
               }
             }
             """, """
             class TrailingWhitespace {
               void foo(String name) {
                   String s = "Hello " + name + "\\n";
               }
             }
             """);
  }

  public void testOverrideStringProcessor() {
    doTest("""
             class StringTemplateMigration {
               public static final String STR = "surprise!";
               void test() {
                 String name = "World";
                 String test = java.lang.StringTemplate.STR."<caret>Hello \\{name}!!!";
               }
               private static class StringTemplate {}
             }""", """
             class StringTemplateMigration {
               public static final String STR = "surprise!";
               void test() {
                 String name = "World";
                 String test = "Hello " + name + "!!!";
               }
               private static class StringTemplate {}
             }""");
  }

  public void testNoTemplate() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = STR."<caret>1 = number";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = "1 = number";
               }
             }""");
  }

  public void testNumbersPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = STR."<caret>\\{1 + 2} = number = 12";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = 1 + 2 + " = number = 12";
               }
             }""");
  }

  public void testCharacterLiteral() {
    doTest("""
             class Test {
               private String name;
               private Date birthDate;
            
               @Override
               public String toString() {
                 return STR."<caret>Test{name='\\{this.name}', birthDate=\\{this.birthDate}}";
               }
             }""", """
             class Test {
               private String name;
               private Date birthDate;
             
               @Override
               public String toString() {
                 return "Test{name='" + this.name + "', birthDate=" + this.birthDate + "}";
               }
             }""");
  }

  public void testDivide() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 String test = STR."<caret>\\{i/3} = number";
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 String test = i / 3 + " = number";
               }
             }""");
  }

  public void testCallAMethod() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = STR.""\"
             fun() = <caret>\\{get("foo")}
             fun() = \\{this.get("bar")}""\";
               }
               StringTemplateMigration get(String data) {
                 return this;
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 String test = "fun() = " + get("foo") + "\\nfun() = " + this.get("bar");
               }
               StringTemplateMigration get(String data) {
                 return this;
               }
             }""");
  }

  public void testCallAStaticMethod() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = STR.""\"
             fun(<caret>) = \\{StringTemplateMigration.sum(7, 8)}
             fun() = \\{sum(9, 10)}""\";
               }
               static int sum(int a, int b) {
                 return a + b;
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 String test = "fun() = " + StringTemplateMigration.sum(7, 8) + "\\nfun() = " + sum(9, 10);
               }
               static int sum(int a, int b) {
                 return a + b;
               }
             }""");
  }

  public void testKeepComments() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 final String action = "Hello";
                 String name = "World";
                 String test = STR/*1*/./*2*/"<caret>\\{/*3*/action/*4*/} \\{/*5*/name/*6*/}!!!"/*7*/;//8
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 final String action = "Hello";
                 String name = "World";
                   /*1*/
                   /*2*/
                   /*3*/
                   /*4*/
                   /*5*/
                   /*6*/
                   String test = action + " " + name + "!!!"/*7*/;//8
               }
             }""");
  }

  public void testTernaryOperator() {
    doTest("""
             class StringTemplateMigration {
               void test(String b) {
                 System.out.println(STR."<caret>\\{true ? "a" : b}c");
               }
             }""", """
             class StringTemplateMigration {
               void test(String b) {
                 System.out.println((true ? "a" : b) + "c");
               }
             }""");
  }

  public void testNumberTypesBeforeString() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 System.out.println(STR."<caret>\\{i + 0.2f + 1.1 + 1_000_000 + 7l + 0x0f + 012 + 0b11} = number");
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 System.out.println(i + 0.2f + 1.1 + 1_000_000 + 7l + 0x0f + 012 + 0b11 + " = number");
               }
             }""");
  }

  public void testNumberTypesAfterString() {
    doTest("""
             class StringTemplateMigration {
               void test(String s) {
                 System.out.println(STR."<caret>\\{s} = 11.127\\{0.2f}\\{1_000_000}\\{7l}\\{0x0f}\\{012}\\{0b11}1.21.31.41.5");
               }
             }""", """
             class StringTemplateMigration {
               void test(String s) {
                 System.out.println(s + " = 11.127" + 0.2f + 1_000_000 + 7l + 0x0f + 012 + 0b11 + "1.21.31.41.5");
               }
             }""");
  }


  public void testOnlyVars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String str = "_";
                 System.out.println(STR."<caret>\\{str}\\{str}\\{str}");
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String str = "_";
                 System.out.println(str + str + str);
               }
             }""");
  }

  public void testNewString() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 System.out.println(STR."<caret>\\{new String("_")}\\{new String("_")}");
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 System.out.println(new String("_") + new String("_"));
               }
             }""");
  }

  public void testIntVars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int value = 17;
                 System.out.println(STR."<caret>\\{value + value}\\{value}\\{value}");
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 int value = 17;
                 System.out.println(value + value + "" + value + value);
               }
             }""");
  }

  public void testEscapeChars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int quote = "\\"";
                 System.out.println(STR.""\"
             <caret>\\{quote}
              \\\\ " \\t \\b \\r \\f ' ©©\\{quote}""\");
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 int quote = "\\"";
                 System.out.println(quote + "\\n \\\\ \\" \\t \\b \\r \\f ' ©©" + quote);
               }
             }""");
  }

  public void testNullValue() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 System.out.println(STR."<caret>text is \\{}null");
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 System.out.println("text is " + null + "null");
               }
             }""");
  }

  public void testTextBlocks() {
    doTest("""
             class TextBlock {
               String name = "Java21";
             
               String message = STR.""\"
             Hello\\{name}! <caret>Text block "example".
             ""\";
             }
             """,
           """
             class TextBlock {
               String name = "Java21";
             
               String message = "Hello" + name + "! Text block \\"example\\".\\n";
             }
             """);
  }

  public void testFormatting() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int requestCode = 200;
             
                 String helloJSON =
                         STR.""\"
             {<caret>
               "cod": \\"\\{requestCode}",
               "message": 0,
               "cnt": 40,
               "city": {
                 "id": 524901,
                 "name": "ABC",
                 "coord": {
                   "lat": 55.7522,
                   "lon": 37.6156
                 },
                 "country": "XY",
                 "population": 0,
                 "timezone": 10800,
                 "sunrise": 1688431913,
                 "sunset": 1688494529
               }
             }""\";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 int requestCode = 200;
             
                 String helloJSON =
                         "{\\n  \\"cod\\": \\"" + requestCode + "\\",\\n  \\"message\\": 0,\\n  \\"cnt\\": 40,\\n  \\"city\\": {\\n    \\"id\\": 524901,\\n    \\"name\\": \\"ABC\\",\\n    \\"coord\\": {\\n      \\"lat\\": 55.7522,\\n      \\"lon\\": 37.6156\\n    },\\n    \\"country\\": \\"XY\\",\\n    \\"population\\": 0,\\n    \\"timezone\\": 10800,\\n    \\"sunrise\\": 1688431913,\\n    \\"sunset\\": 1688494529\\n  }\\n}";
               }
             }""");
  }

  private void doTest(@NotNull @Language("Java") String before, @NotNull @Language("Java") String after) {
    myFixture.configureByText("Template.java", before);
    myFixture.enableInspections(new StringTemplateReverseMigrationInspection());
    myFixture.launchAction(myFixture.findSingleIntention(JavaBundle.message("inspection.replace.with.string.concatenation.fix")));
    myFixture.checkResult(after);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }
}