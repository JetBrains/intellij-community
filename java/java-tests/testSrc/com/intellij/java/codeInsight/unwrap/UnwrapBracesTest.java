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

@TestApplication
public class UnwrapBracesTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertOptions(String code, String... expectedOptions) {
    UnwrapTestUtil.assertOptions(codeInsightFixture.get(), code, expectedOptions);
  }

  @Test
  public void testBraces() {
    assertUnwrapped("""
                      {
                          int i;<caret>
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testEmptyBraces() {
    assertUnwrapped("""
                      {
                          {<caret>}
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  @Test
  public void testBracesWithComments() {
    assertUnwrapped("""
                      {
                          // a <caret>comment
                          int i = 0;
                      }
                      """,

                    """
                      // a <caret>comment
                      int i = 0;
                      """);
  }

  @Test
  public void testTrimmingTheLeadingAndTrailingWhileSpaces() {
    assertUnwrapped("""
                      {
                         \s
                         \s
                          int i<caret> = 0;
                         \s
                         \s
                      }
                      """,

                    "int i<caret> = 0;\n");
  }

  @Test
  public void testBracesOptions() {
    assertOptions("""
                    {
                        {
                            int i;<caret>
                        }
                    }
                    """,

                  "Unwrap braces",
                  "Unwrap braces");
  }
}
