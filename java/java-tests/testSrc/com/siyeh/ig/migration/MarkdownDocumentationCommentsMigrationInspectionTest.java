// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@TestDataPath("$CONTENT_ROOT/testData/ig/com/siyeh/igtest/migration/markdown_documentation_comments_migration")
public class MarkdownDocumentationCommentsMigrationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MarkdownDocumentationCommentsMigrationInspection());

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