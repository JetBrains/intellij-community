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
public class UnwrapForTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  @Test
  public void testForWithStatement() {
    assertUnwrapped("for(int i = 0; i < 10; i++) Sys<caret>tem.gc();\n",

                    """
                      int i = 0;
                      Sys<caret>tem.gc();
                      """);
  }

  @Test
  public void testForWithBlock() {
    assertUnwrapped("""
                      for(int i = 0; i < 10; i++) {
                          Sys<caret>tem.gc();
                      }
                      """,

                    """
                      int i = 0;
                      Sys<caret>tem.gc();
                      """);
  }

  @Test
  public void testEmptyFor() {
    assertUnwrapped("for<caret>(int i = 0; i < 10; i++);\n",

                    "int i = 0;<caret>\n");
  }

  @Test
  public void testForEachWithStatement() {
    assertUnwrapped("for(String s : strings) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }
  
  @Test
  public void testForEachWithBlock() {
    assertUnwrapped("""
                      for(String s : strings) {
                          Sys<caret>tem.gc();
                      }
                      """,

                    "Sys<caret>tem.gc();\n");
  }
}