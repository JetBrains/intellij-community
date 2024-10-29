// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveDirectoryUsageInfo extends UsageInfo {
  private final PsiFileSystemItem myTargetFileItem;

  public MoveDirectoryUsageInfo(@NotNull PsiDirectory sourceDirectory, @NotNull PsiDirectory targetDirectory) {
    super(sourceDirectory);
    myTargetFileItem = targetDirectory;
  }

  public MoveDirectoryUsageInfo(@NotNull PsiReference reference, @NotNull PsiFileSystemItem targetFileItem) {
    super(reference);
    myTargetFileItem = targetFileItem;
  }

  @Override
  public @Nullable PsiReference getReference() {
    PsiElement element = getElement();
    if (element == null) return null;
    final ProperTextRange rangeInElement = getRangeInElement();
    PsiReference reference = rangeInElement != null ? element.findReferenceAt(rangeInElement.getStartOffset()) : element.getReference();
    return reference != null && reference.getRangeInElement().equals(rangeInElement) ? reference : null;
  }

  public @Nullable PsiDirectory getSourceDirectory() {
    return ObjectUtils.tryCast(getElement(), PsiDirectory.class);
  }

  public @NotNull PsiFileSystemItem getTargetFileItem() {
    return myTargetFileItem;
  }
}
