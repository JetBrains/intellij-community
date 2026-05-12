// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MarkdownDocumentationCommentsMigrationInspectionTest extends LightJavaInspectionTestCase {

  public void testMarkdownDocumentationCommentsMigration() { check(); }
  public void testReferencesNoEscape() { check(); }
  public void testCodeBlocks() { check(); }


  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new MarkdownDocumentationCommentsMigrationInspection();
  }
  
  private void check() {
    doTest();
    checkQuickFixAll();
  }
  
}