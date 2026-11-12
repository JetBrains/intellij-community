// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.bool;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class FlipComparisonIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testSimple() {
    ipp.get().doTest("""
             class X {  boolean b = 1//some comment
              >/*_Flip '>' to '<'*///another comment
              2;}""",

           """
             class X {  //some comment
                 //another comment
                 boolean b = 2 < 1;}""");
  }

  @Test
  public void testAssignment() {
    ipp.get().doTest("""
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

  @Test
  public void testGreater() {
    ipp.get().doTest("class X {{" +
           "  if(a+b>/*_Flip '>' to '<'*/c);" +
           "}}",

           "class X {{" +
           "  if(c < a + b);" +
           "}}");
  }

  @Test
  public void testBrokenCode() {
    ipp.get().doTestIntentionNotAvailable("import java.util.*;" +
                                "class X {" +
                                "  String x(Set<String> set) {" +
                                "  }" +
                                "  {x(HashSet</*_Flip '>' to '<'*/>());}" +
                                "}");
  }

  @Test
  public void testBrokenCode2() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class Builder {
                                      Builder b = !(new//simple end comment
                                           </*_Flip '>=' to '<='*/>   Builder().method( >= caret) >1).method(2);
                                  }""");
  }

  @Test
  public void testBrokenCode3() {
    ipp.get().doTest("""
             @Anno(
               param//test comment
               /*_Flip '<' to '>'*/<foo>""",

           """
             @Anno(
                     //test comment
                     foo > param >)""");
  }

  @Test
  public void testBrokenCode4() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      ""<foo>"//simple/*_Flip '>' to '<'*/ end comment
                                    }
                                  }""");
  }

  @Test
  public void testBrokenCode5() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      /*_Flip '>' to '<'*/a > b > c  }
                                  }""");
  }

  @Test
  public void testBrokenCode6() {
    ipp.get().doTestIntentionNotAvailable("""
                                  class A{
                                    {
                                      ((LookupElementBuilder)variants[0]).rendeFragment>/*_Flip '>' to '<'*/ fragments = presentation.getTailFragments();  }
                                  }""");
  }

  @Test
  public void testNoop() {
    ipp.get().doTestIntentionNotAvailable("class X {" +
                                "  void x(String x) {" +
                                "    if (x /*_Flip '=='*/== x) {}" +
                                "  }" +
                                "}");
  }
}