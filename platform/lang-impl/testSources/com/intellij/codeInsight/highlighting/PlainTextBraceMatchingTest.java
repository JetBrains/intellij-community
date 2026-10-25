// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.BraceHighlightingTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * Checks the brace matching in a file without a language specific brace matcher.
 */
@TestApplication
@RunInEdt(writeIntent = true)
public class PlainTextBraceMatchingTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testUnknownFileType() {
    codeInsightFixture.get().configureByText("cs.cs", "void a() <caret>{ print(\"b\"); <pair>}");
    BraceHighlightingTestUtil.assertBraceMatched(codeInsightFixture.get().getFile(), codeInsightFixture.get().getEditor());
  }

  @Test
  public void testTxtWithNonMatchedBraceBetweenMatched() {
    codeInsightFixture.get().configureByText("a.txt", "<caret>{><pair>}");
    BraceHighlightingTestUtil.assertBraceMatched(codeInsightFixture.get().getFile(), codeInsightFixture.get().getEditor());
  }
}
