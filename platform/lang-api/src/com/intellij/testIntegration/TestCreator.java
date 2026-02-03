// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Implementations of this extension are used on generating tests while navigation using GotoTestOrCodeAction.
 * Corresponding extension point qualified name is {@code com.intellij.testCreator}, see {@link com.intellij.testIntegration.LanguageTestCreators}
 * <p>
 * To decorate creating test action consider implementing {@link ItemPresentation}.
 */
public interface TestCreator extends PossiblyDumbAware {

  /**
   * Should "Create Test" action be available in this context?
   */
  boolean isAvailable(Project project, Editor editor, PsiFile file);

  /**
   * Triggered when the user actually selects the "Create New Test..." action from the popup. Responsible for actually creating the test file.
   */
  void createTest(Project project, Editor editor, PsiFile file);
}
