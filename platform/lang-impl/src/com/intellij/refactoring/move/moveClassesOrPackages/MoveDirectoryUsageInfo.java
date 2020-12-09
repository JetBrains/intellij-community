// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  public PsiReference getReference() {
    PsiElement element = getElement();
    if (element == null) return null;
    final ProperTextRange rangeInElement = getRangeInElement();
    PsiReference reference = rangeInElement != null ? element.findReferenceAt(rangeInElement.getStartOffset()) : element.getReference();
    return reference != null && reference.getRangeInElement().equals(rangeInElement) ? reference : null;
  }

  @Nullable
  public PsiDirectory getSourceDirectory() {
    return ObjectUtils.tryCast(getElement(), PsiDirectory.class);
  }

  @NotNull
  public PsiFileSystemItem getTargetFileItem() {
    return myTargetFileItem;
  }
}
