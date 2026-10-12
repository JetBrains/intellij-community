// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class UnwrapConditionalTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<Path> pathFixture = tempPathFixture();
  @SuppressWarnings("unused")
  private final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, pathFixture, true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture, pathFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter, int option) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, option);
  }

  @Test
  public void testThat() {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n",
                    "xxx('1');\n");
  }

  @Test
  public void testElse() {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n",
                    "xxx('2' +<caret> 3);\n");
  }

  @Test
  public void testFromParameterList2() {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n",
                    "xxx(11, '2' +<caret> 3, 12);\n");
  }

  @Test
  public void testCond1() {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  @Test
  public void testCond2() {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  @Test
  public void testUnwrapUnderAssignmentExpression() {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n",
                    "String s = \"1\";\n");
  }
}