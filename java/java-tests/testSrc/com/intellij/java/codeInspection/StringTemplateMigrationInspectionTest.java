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

  public void testNumberPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test(String s) {
                 String test = 1 + <caret>" = " + s;
               }
             }""", """
             class StringTemplateMigration {
               void test(String s) {
                 String test = STR."1 = \\{s}";
               }
             }""");
  }

  public void testNumbersPlusString() {
    doTest("""
             class StringTemplateMigration {
               void test(int i) {
                 String test = 1 + i + <caret>" = number = " + 1 + 2;
               }
             }""", """
             class StringTemplateMigration {
               void test(int i) {
                 String test = STR."\\{1 + i} = number = 12";
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
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = STR."fun() = \\{get("foo")}\\nfun() = \\{this.get("bar")}";
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
             }""", """
             class StringTemplateMigration {
               void test() {
                 String test = STR."fun() = \\{StringTemplateMigration.sum(7, 8)}\\nfun() = \\{sum(9, 10)}";
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
             }""", """
             class StringTemplateMigration {
               void test() {
                 int quote = "\\"";
                 System.out.println(STR."\\{quote}\\n \\\\ \\" \\t \\b \\r \\f \\' \\u00A9\\u00A9\\{quote}");
               }
             }""");
  }

  public void testAnnotationParameterIgnored() {
    myFixture.configureByText("Template.java", """
      interface X {
        String s = "one";
        
        @SuppressWarnings("x" + s + "y") void x();
      }
      """);

    myFixture.enableInspections(new StringTemplateMigrationInspection());
    myFixture.testHighlighting(true, true, true);
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

  private void doTest(@NotNull @Language("Java") String before, @NotNull @Language("Java") String after) {
    myFixture.configureByText("Template.java", before);

    myFixture.enableInspections(new StringTemplateMigrationInspection());
    myFixture.launchAction(myFixture.findSingleIntention("Replace with string template"));

    myFixture.checkResult(after);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }
}