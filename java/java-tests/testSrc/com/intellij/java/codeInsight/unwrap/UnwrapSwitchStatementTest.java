// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

/**
 * @author Bas Leijdekkers
 */
public class UnwrapSwitchStatementTest extends UnwrapTestCase {

  public void testSimple() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1:
                              b = <caret>false;
                              break;
                          default:
                              b = true;
                              break;
                      }""",

                    "boolean b;\n" +
                    "b = false;");
  }

  public void testNoBreak() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1:
                              b = false;
                              break;
                          default:
                              b = <caret>true;
                      }""",

                    "boolean b;\n" +
                    "b = true;");
  }

  public void testLabeledBreak() {
    assertUnwrapped("""
                      outer: for (int z = 0; z < 10; z++) {
                          boolean b;
                          switch (0) {
                              case 1:
                      <caret>            b = false;
                                  break outer;
                              default:
                                  b = true;
                                  break;
                          }
                      }""",

                    """
                      outer: for (int z = 0; z < 10; z++) {
                          boolean b;
                          b = false;
                          break outer;
                      }
                      """);
  }

  public void testFallThrough() {
    assertUnwrapped("""
                      switch (0) {
                          <caret>case 1:
                              System.out.println(1);
                          default:
                              System.out.println(2);
                              break;
                      }""",

                    "System.out.println(1);\n" +
                    "System.out.println(2);");
  }

  public void testEmptySwitch() {
    assertUnwrapped("""
                      switch (1) {
                          case 1:<caret>
                      }""",

                    "");
  }

  public  void testThrowStatement() {
    assertUnwrapped("""
                      switch (0) {
                          case 1: {
                              System.out.println();
                              break;
                          }
                          case 2: <caret>throw null;
                          default:
                              System.out.println();
                              return;
                      }""",

                    "throw null;");
  }

  public void testRuleBasedBlock() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1 -> {
                              <caret>System.out.println();
                              b = false;
                          }
                          case 2 -> throw null;
                          default -> b = true;
                      }""",

                    """
                      boolean b;
                      System.out.println();
                      b = false;""");
  }

  public void testRuleBasedExpression() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1 -> {
                              System.out.println();
                              b = false;
                          }
                          case 2 -> throw null;
                          default<caret> -> b = true;
                      }""",

                    "boolean b;\n" +
                    "b = true;");
  }

  public void testRuleBasedThrowStatement() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1 -> {
                              System.out.println();
                              b = false;
                          }
                          case 2 -> <caret>throw null;
                          default -> b = true;
                      }""",

                    "boolean b;\n" +
                    "throw null;");
  }

  public void testCommentBracesBreak() {
    assertUnwrapped("""
                      boolean b;
                      switch (0) {
                          case 1: {
                              System.out.println();
                              b = false;<caret>
                              if (b) break;
                          }
                          default:
                              b = true;
                              //asdf
                              b = true;
                              break;
                      }""",

                    """
                      boolean b;
                      System.out.println();
                      b = false;
                      if (b) {
                      }
                      b = true;//asdf
                      b = true;""",
                    1);
  }

  public void testNestedSwitch() {
    assertUnwrapped("""
                      switch (0) {
                          case 1:
                              switch (1) {
                                  case 2:
                                      System.out.println(1);
                                      break;
                              }
                              System.out.println(2);<caret>
                              break;
                          default:
                              System.out.println(3);
                      }""",

                    """
                      switch (1) {
                          case 2:
                              System.out.println(1);
                              break;
                      }
                      System.out.println(2);""");
  }

  public void testNestedWhileWithBreak() {
    assertUnwrapped("""
                      switch (0) {
                          case 1:
                              while (true) {
                                  break;
                              }
                              System.out.println(1);<caret>
                              break;
                      }
                      """,

                    """
                      while (true) {
                          break;
                      }
                      System.out.println(1);""");
  }
}
