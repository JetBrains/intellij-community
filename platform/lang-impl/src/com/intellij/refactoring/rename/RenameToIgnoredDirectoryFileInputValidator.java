// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

public class RenameToIgnoredDirectoryFileInputValidator implements RenameInputValidatorEx {
  @Override
  public @Nullable String getErrorMessage(String newName, Project project) {
    if (FileTypeManager.getInstance().isFileIgnored(newName)) {
      return RefactoringBundle.message("dialog.message.new.directory.with.ignored.name.warning");
    }
    return null;
  }

  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.or(PlatformPatterns.psiElement(PsiDirectory.class), PlatformPatterns.psiElement(PsiFile.class));
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return newName != null && newName.length() > 0 && newName.indexOf('\\') < 0 && newName.indexOf('/') < 0 && newName.indexOf('\n') < 0;
  }
}
