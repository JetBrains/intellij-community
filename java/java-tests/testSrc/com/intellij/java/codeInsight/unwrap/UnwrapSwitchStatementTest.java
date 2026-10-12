// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnwrapSwitchStatementTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter, int option) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, option);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testEmptySwitch() {
    assertUnwrapped("""
                      switch (1) {
                          case 1:<caret>
                      }""",

                    "");
  }

  @Test
  public void testThrowStatement() {
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
