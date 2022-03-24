// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringActionHandler;

public interface ChangeTypeSignatureHandlerBase extends RefactoringActionHandler {
  void runHighlightingTypeMigrationSilently(final Project project,
                                            final Editor editor,
                                            final SearchScope boundScope,
                                            final PsiElement root,
                                            final PsiType migrationType);
}