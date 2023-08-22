// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.bool;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FlipComparisonIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest("""
             class X {  boolean b = 1//some comment
              >/*_Flip '>' to '<'*///another comment
              2;}""",

           """
             class X {  //some comment
                 //another comment
                 boolean b = 2 < 1;}""");
  }

  public void testAssignment() {
    doTest("""
             class X {
               void foo(int x) {
                 boolean b;
                 b = 1/*_Flip '>' to '<'*/ > x;
               }
             }""",

           """
             class X {
               void foo(int x) {
                 boolean b;
                 b = x < 1;
               }
             }""");
  }

  public void testGreater() {
    doTest("class X {{" +
           "  if(a+b>/*_Flip '>' to '<'*/c);" +
           "}}",

           "class X {{" +
           "  if(c < a + b);" +
           "}}");
  }

  public void testBrokenCode() {
    doTestIntentionNotAvailable("import java.util.*;" +
                                "class X {" +
                                "  String x(Set<String> set) {" +
                                "  }" +
                                "  {x(HashSet</*_Flip '>' to '<'*/>());}" +
                                "}");
  }

  public void testBrokenCode2() {
    doTestIntentionNotAvailable("""
                                  class Builder {
                                      Builder b = !(new//simple end comment
                                           </*_Flip '>=' to '<='*/>   Builder().method( >= caret) >1).method(2);
                                  }""");
  }

  public void testBrokenCode3() {
    doTest("""
             @Anno(
               param//test comment
               /*_Flip '<' to '>'*/<foo>""",

           """
             @Anno(
                     //test comment
                     foo > param >)""");
  }

  public void testBrokenCode4() {
    doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      ""<foo>"//simple/*_Flip '>' to '<'*/ end comment
                                    }
                                  }""");
  }

  public void testBrokenCode5() {
    doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      /*_Flip '>' to '<'*/a > b > c  }
                                  }""");
  }

  public void testBrokenCode6() {
    doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      ((LookupElementBuilder)variants[0]).rendeFragment>/*_Flip '>' to '<'*/ fragments = presentation.getTailFragments();  }
                                  }""");
  }

  public void testNoop() {
    doTestIntentionNotAvailable("class X {" +
                                "  void x(String x) {" +
                                "    if (x /*_Flip '=='*/== x) {}" +
                                "  }" +
                                "}");
  }
}