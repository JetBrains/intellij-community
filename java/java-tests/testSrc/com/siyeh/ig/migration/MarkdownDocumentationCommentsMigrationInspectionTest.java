// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
@TestDataPath("$CONTENT_ROOT/testData/ig/com/siyeh/igtest/migration/markdown_documentation_comments_migration")
public class MarkdownDocumentationCommentsMigrationInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new MarkdownDocumentationCommentsMigrationInspection());

  @Test
  public void testMarkdownDocumentationCommentsMigration() { check(); }
  @Test
  public void testReferencesNoEscape() { check(); }
  @Test
  public void testCodeBlocks() { check(); }
  @Test
  public void testDeprecatedUsages() { check(); }
  
  @Test
  public void testAllTags() { check(); }
  
  
  private void check() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}