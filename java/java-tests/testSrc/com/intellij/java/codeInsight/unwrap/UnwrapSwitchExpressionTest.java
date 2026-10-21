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
public class UnwrapSwitchExpressionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  @Test
  public void testSimple() {
    assertUnwrapped("""
                      boolean b = switch(0) {
                                  case 1 -> <caret>false;
                                  default -> true;
                              };""",
                    "boolean b = false;");
  }
}
