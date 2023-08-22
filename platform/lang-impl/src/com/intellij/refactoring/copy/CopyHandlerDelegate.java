// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.copy;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;


public interface CopyHandlerDelegate {
  ExtensionPointName<CopyHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.copyHandler");

  boolean canCopy(PsiElement[] elements);
  void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory);
  void doClone(PsiElement element);

  @Nullable
  @Nls(capitalization = Nls.Capitalization.Title)
  default String getActionName(PsiElement[] elements) {
    return null;
  }
}
