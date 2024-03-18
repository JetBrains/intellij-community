// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.StringTemplateMigrationInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @see StringTemplateMigrationInspection
 */
public class StringTemplateMigrationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String name = "World";
                 String test = "Hello " + na<caret>me  + "!" + "!" + "!";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String name = "World";
                 String test = STR."Hello \\{name}!!!";
               }
             }""");
  }

  public void testOverrideStringProcessor() {
    doTest("""
             class StringTemplateMigration {           
               public static final String STR = "surprise!";
               void test() {
                 String name = "World";
                 String test = "Hello " + na<caret>me  + "!" + "!" + "!";
               }          
               private static class StringTemplate {}
             }""", """
             class StringTemplateMigration {
               public static final String STR = "surprise!";
               void test() {
                 String name = "World";
                 String test = java.lang.StringTemplate.STR."Hello \\{name}!!!";
               }
               private static class StringTemplate {}
             }""");
  }

  public void testNumberPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = 1 + <caret>" = number";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = STR."1 = number";
               }
             }""");
  }

  public void testNumbersPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = 1 + 2 + <caret>" = number = " + 1 + 2;
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = STR."\\{1 + 2} = number = 12";
               }
             }""");
  }

  public void testParenthesizedPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 String test = (((1 + 2) - (3*i))) + <caret>" = number = "+(1+2);
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 String test = STR."\\{(1 + 2) - (3 * i)} = number = \\{1 + 2}";
               }
             }""");
  }

  public void testDivide() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 String test = i/3 + <caret>" = number";
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 String test = STR."\\{i / 3} = number";
               }
             }""");
  }

  public void testCallAMethod() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String test = "fun() = " + <caret>get("foo") + "\\n" +
                               "fun() = " + this.get("bar");
               }
               StringTemplateMigration get(String data) {
                 return this;
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 String test = STR.""\"
             fun() = \\{get("foo")}
             fun() = \\{this.get("bar")}""\";
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
                 String test = "fun() = " + <caret>StringTemplateMigration.sum(7, 8) + "\\n" +
                               "fun() = " + sum(9,10);
               }
               static int sum(int a, int b) {
                 return a + b;
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 String test = STR.""\"
             fun() = \\{StringTemplateMigration.sum(7, 8)}
             fun() = \\{sum(9, 10)}""\";
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
                 String test = action + <caret>" " +
                 // comment 1
                 name +
                 /* comment 2 */ "!" /*
                 comment
                 3
                 */ + "!" +
                 /**
                 comment
                 4
                 */        "!";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 final String action = "Hello";
                 String name = "World";
                   // comment 1
                   /* comment 2 */
                 /*
                 comment
                 3
                 */
                   /**
                    comment
                    4
                    */
                   String test = STR."\\{action} \\{name}!!!";
               }
             }""");
  }

  public void testTernaryOperator() {
    doTest("""
             class StringTemplateMigration {
               void test(String b) {
                 System.out.println((true ? "a" : b) + <caret>"c");
               }
             }""", """
             class StringTemplateMigration {
               void test(String b) {
                 System.out.println(STR."\\{true ? "a" : b}c");
               }
             }""");
  }

  public void testNumberTypesBeforeString() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 System.out.println(i+0.2f+1.1+1_000_000+7l+0x0f+012+0b11 + <caret>" = number");
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 System.out.println(STR."\\{i + 0.2f + 1.1 + 1_000_000 + 7l + 0x0f + 012 + 0b11} = number");
               }
             }""");
  }

  public void testNumberTypesAfterString() {
    doTest("""
             class StringTemplateMigration {
               void test(String s) {
                 System.out.println(s + " = "<caret> + 1+1.1+2+7+0.2f+1_000_000+7l+0x0f+012+0b11+1.2+1.3+1.4+1.5);
               }
             }""", """
             class StringTemplateMigration {
               void test(String s) {
                 System.out.println(STR."\\{s} = 11.127\\{0.2f}\\{1_000_000}\\{7l}\\{0x0f}\\{012}\\{0b11}1.21.31.41.5");
               }
             }""");
  }


  public void testOnlyVars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 String str = "_";
                 System.out.println(str + str + <caret>str);
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 String str = "_";
                 System.out.println(STR."\\{str}\\{str}\\{str}");
               }
             }""");
  }

  public void testNewString() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 System.out.println(new String("_") + <caret>new String("_"));
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 System.out.println(STR."\\{new String("_")}\\{new String("_")}");
               }
             }""");
  }

  public void testIntVars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int value = 17;
                 System.out.println(value +<caret> value + "" + value + value);
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 int value = 17;
                 System.out.println(STR."\\{value + value}\\{value}\\{value}");
               }
             }""");
  }

  public void testEscapeChars() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int quote = "\\"";
                 System.out.println(quote <caret> + "\\n \\\\ \\" \\t \\b \\r \\f \\' \\u00A9" + "\\u00A9" + quote);
               }
             }""",
           """
             class StringTemplateMigration {
               void test() {
                 int quote = "\\"";
                 System.out.println(STR.""\"
             \\{quote}
              \\\\ " \\t \\b \\r \\f ' ©©\\{quote}""\");
               }
             }""");
  }

  public void testAnnotationParameterIgnored() {
    assertNoHighlightNoQuickFix("""
      interface X {
        String s = "one";
        
        @SuppressWarnings("x" +<caret> s + "y") void x();
      }""");
  }

  public void testSwitchCaseLabelIgnored() {
    assertNoHighlightNoQuickFix("""
      class X {
        static void x(String s) {
          switch (s) {
            case "asdf" <caret>+ 1:
              System.out.println(s);
          }
        }
      }""");
  }

  public void testAnnotationMethodIgnored() {
    assertNoHighlightNoQuickFix("""
      @interface X {
        String x() default "number " <caret>+ 1;
      }
      """);
  }

  public void testNullValue() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 System.out.println("text is "<caret> + (String)null + "" + null);
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 System.out.println(STR."text is \\{(String) null}null");
               }
             }""");
  }

  public void testTextBlocks() {
    doTest("""
             class TextBlock {
               String name = "Java21";
               
               String message = ""\"
                   Hello ""\" + <caret>name + ""\"
                   ! Text block "example".
                   ""\";
             }
             """,
           """
             class TextBlock {
               String name = "Java21";
                        
               String message = STR.""\"
             Hello\\{name}! Text block "example".
             ""\";
             }
             """);
  }

  public void testFormatting() {
    doTest("""
             class StringTemplateMigration {
               void test() {
                 int requestCode = 200;
                 
                 String helloJSON =
                 "{\\n" +
                 "  \\"cod\\": \\"" + <caret>requestCode + "\\",\\n" +
                 "  \\"message\\": 0,\\n" +
                 "  \\"cnt\\": 40,\\n" +
                 "  \\"city\\": {\\n" +
                 "    \\"id\\": 524901,\\n" +
                 "    \\"name\\": \\"ABC\\",\\n" +
                 "    \\"coord\\": {\\n" +
                 "      \\"lat\\": 55.7522,\\n" +
                 "      \\"lon\\": 37.6156\\n" +
                 "    },\\n" +
                 "    \\"country\\": \\"XY\\",\\n" +
                 "    \\"population\\": 0,\\n" +
                 "    \\"timezone\\": 10800,\\n" +
                 "    \\"sunrise\\": 1688431913,\\n" +
                 "    \\"sunset\\": 1688494529\\n" +
                 "  }\\n" +
                 "}";
               }
             }""", """
             class StringTemplateMigration {
               void test() {
                 int requestCode = 200;
             
                 String helloJSON =
                         STR.""\"
             {
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
             }""");
  }

  private void doTest(@NotNull @Language("Java") String before, @NotNull @Language("Java") String after) {
    myFixture.configureByText("Template.java", before);

    myFixture.enableInspections(new StringTemplateMigrationInspection());
    myFixture.launchAction(myFixture.findSingleIntention("Replace with string template"));

    myFixture.checkResult(after);
  }

  private void assertNoHighlightNoQuickFix(@NotNull @Language("Java") String code) {
    myFixture.configureByText("Template.java", code);

    myFixture.enableInspections(new StringTemplateMigrationInspection());
    myFixture.testHighlighting(true, true, true);
    assertEmpty("Quickfix is available but should not",
                myFixture.filterAvailableIntentions("Replace with string template"));
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }
}