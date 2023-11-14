// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.whileloop;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class WhileCanBeDoWhileInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testReplaceSingleStatement() {
    doTest("""
             class DoWhileClass {
               void test() {
                 System.out.println(1);
                 while<caret>(condition()) {
                   System.out.println(1);
                 }
               }
               boolean condition() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                   do {
                       System.out.println(1);
                   } while (condition());
               }
               boolean condition() { return true; }
             }""");
  }

  public void testReplaceSingleStatementWithoutCodeBlock() {
    doTest("""
             class DoWhileClass {
               void test() {
                 System.out.println(1);
                 while<caret>(condition())
                   System.out.println(1);
               }
               boolean condition() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                   do System.out.println(1);
                   while (condition());
               }
               boolean condition() { return true; }
             }""");
  }

  public void testReplaceCodeBlock() {
    doTest("""
             class DoWhileClass {
               void test() {
                 int sum = 0;
                 {
                   sum += 1;
                   print(sum);
                   int a = 2;
                   sum = sum * a;
                 }
                 while<caret>(sum < 100) {
                   sum += 1;
                   print(sum);
                   int b = 2;
                   sum = sum * b;
                 }
               }
               void print(int a) {
                 System.out.println(a);
               }
             }""", """
             class DoWhileClass {
               void test() {
                 int sum = 0;
                   do {
                       sum += 1;
                       print(sum);
                       int b = 2;
                       sum = sum * b;
                   } while (sum < 100);
               }
               void print(int a) {
                 System.out.println(a);
               }
             }""");
  }

  public void testReplaceCodeWithComments() {
    doTest("""
             class DoWhileClass {
               void test() {
                 int sum = 0;
                 // comment 0
                 sum += 1;
                 // comment 1
                 print(sum);
                 
                 int a = 2;
                 
                 sum = sum * a;
                 // comment 2
                 while<caret>(sum < 100) {
                 // comment 3
                   sum += 1;
                   print(sum);
                   int b = 2;
                   sum = sum * b;
                 }
               }
               void print(int a) {
                 System.out.println(a);
               }
             }""", """
             class DoWhileClass {
               void test() {
                 int sum = 0;
                 // comment 0
                   // comment 2
                   do {
                       // comment 3
                       sum += 1;
                       print(sum);
                       int b = 2;
                       sum = sum * b;
                   } while (sum < 100);
               }
               void print(int a) {
                 System.out.println(a);
               }
             }""");
  }

  public void testDifferentBreaks() {
    doTest("""
             class DoWhileClass {
               void test() {
                 for(int i = 0; i < 10; i++) {
                    int j = i;
                    if(condition()) break;
                    j--;
                    while<caret>(j > 4) {
                      if(condition()) break;
                      j--;
                    }
                 }
               }
               boolean condition() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                 for(int i = 0; i < 10; i++) {
                    int j = i;
                    if(condition()) break;
                    j--;
                     if (j > 4) {
                         do {
                             if (condition()) break;
                             j--;
                         } while (j > 4);
                     }
                 }
               }
               boolean condition() { return true; }
             }""");
  }

  public void testEqBreaks() {
    doTest("""
             class DoWhileClass {
               void test() {
                 for(int i = 0; i < 10; i++) {
                    int j = i;
                    for(int k = 0; k < 2; k++) if(condition()) break;
                    j--;
                    while<caret>(j > 4) {
                      for(int k = 0; k < 2; k++) if(condition()) break;
                      j--;
                    }
                 }
               }
               boolean condition() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                 for(int i = 0; i < 10; i++) {
                    int j = i;
                     do {
                         for (int k = 0; k < 2; k++) if (condition()) break;
                         j--;
                     } while (j > 4);
                 }
               }
               boolean condition() { return true; }
             }""");
  }

  public void testTrue() {
    doTest("""
             class DoWhileClass {
               void test() {
                 int sum = 0;
                 sum += 1;
                 System.out.println("sum");
                 while<caret>(true) {
                   sum += 1;
                   System.out.println("sum");
                 }
               }
             }""", """
             class DoWhileClass {
               void test() {
                 int sum = 0;
                   do {
                       sum += 1;
                       System.out.println("sum");
                   } while (true);
               }
             }""");
  }

  public void testEmptyBlock() {
    doTest("""
             class DoWhileClass {
               void test() {
                 while<caret>(true) {
                 }
               }
             }""", """
             class DoWhileClass {
               void test() {
                   do {
                   } while (true);
               }
             }""");
  }

  public void testInfiniteLoop() {
    doTest("""
             class DoWhileClass {
               void test() {
                 while<caret>((true)) {
                   System.out.println(1);
                 }
               }
             }""", """
             class DoWhileClass {
               void test() {
                   do {
                       System.out.println(1);
                   } while ((true));
               }
             }""");
  }

  public void testSwitchCase() {
    doTest("""
             class DoWhileClass {
               void test() {
                 int i = 9;
                 switch (i) {
                   case 7: System.out.println(7);
                   default: i--; break;
                 }
                 while<caret>(i > 2) {
                   switch (i) {
                     case 7: System.out.println(7);
                     default: i--; break;
                   }
                 }
               }
             }""", """
             class DoWhileClass {
               void test() {
                 int i = 9;
                   do {
                       switch (i) {
                           case 7:
                               System.out.println(7);
                           default:
                               i--;
                               break;
                       }
                   } while (i > 2);
               }
             }""");
  }

  public void testNoBraces() {
    doTest("""
             class DoWhileClass {
               void test() {
                 while<caret>//after while
                       (b(/*inside call*/)) //before body
                 System.out.println();
               }
               boolean b() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                   //after while
                   //before body
                   if (b(/*inside call*/)) {
                       do System.out.println();
                       while (b(/*inside call*/));
                   }
               }
               boolean b() { return true; }
             }""");
  }

  public void testRegular() {
    doTest("""
             class DoWhileClass {
               void test() {
                 <caret>while(b()) {
                   System.out.println(1);
                 };
               }
               boolean b() { return true; }
             }""", """
             class DoWhileClass {
               void test() {
                   if (b()) {
                       do {
                           System.out.println(1);
                       } while (b());
                   }
                   ;
               }
               boolean b() { return true; }
             }""");
  }


  public void testDifferentComments() {
    testHighlighting("""
                       class DoWhileClass {
                         void test() {
                           int sum = 0;
                           sum += 1;
                           // comment 1
                           System.out.println("sum");
                           <info descr="Replace 'while' with 'do while'">while</info>(true) {
                             sum += 1;
                             // comment 2
                             System.out.println("sum");
                           }
                         }
                       }""");
  }

  public void testEqComments() {
    testHighlighting("""
                       class DoWhileClass {
                         void test() {
                           int sum = 0;
                           sum += 1;
                           // comment
                           System.out.println("sum");
                           <weak_warning descr="Replace 'while' with 'do while'">while</weak_warning>(true) {
                             sum += 1;
                             // comment
                             System.out.println("sum");
                           }
                         }
                       }""");
  }


  private void doTest(@NotNull @Language("Java") String before, @NotNull @Language("Java") String after) {
    myFixture.configureByText("DoWhile.java", before);

    myFixture.enableInspections(new WhileCanBeDoWhileInspection());
    myFixture.launchAction(myFixture.findSingleIntention("Replace 'while' with 'do while'"));

    myFixture.checkResult(after);
  }

  private void testHighlighting(@NotNull @Language("Java") String code) {
    myFixture.configureByText("DoWhile.java", code);

    myFixture.enableInspections(new WhileCanBeDoWhileInspection());
    myFixture.testHighlighting(true, true, true);
  }
}
