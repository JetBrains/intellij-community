// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import org.jetbrains.annotations.NotNull;

class SmartPsiFileRangePointerImpl extends SmartPsiElementPointerImpl<PsiFile> implements SmartPsiFileRange {
  SmartPsiFileRangePointerImpl(@NotNull SmartPointerManagerImpl manager, @NotNull PsiFile containingFile, @NotNull ProperTextRange range, boolean forInjected) {
    super(manager, containingFile, createElementInfo(containingFile, range, forInjected));
  }

  private static @NotNull SmartPointerElementInfo createElementInfo(@NotNull PsiFile containingFile, @NotNull ProperTextRange range, boolean forInjected) {
    Project project = containingFile.getProject();
    if (containingFile.getViewProvider() instanceof FreeThreadedFileViewProvider) {
      PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(containingFile);
      if (host != null) {
        SmartPsiElementPointer<PsiLanguageInjectionHost> hostPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(host);
        return new InjectedSelfElementInfo(project, containingFile, range, containingFile, hostPointer);
      }
    }
    if (!forInjected && range.equals(containingFile.getTextRange())) return new FileElementInfo(containingFile);
    return new SelfElementInfo(range, Identikit.fromTypes(PsiElement.class, null, Language.ANY), containingFile, forInjected);
  }

  @Override
  public PsiFile getContainingFile() {
    return getElementInfo().restoreFile(myManager);
  }

  @Override
  public PsiFile getElement() {
    if (getRange() == null) return null; // range is invalid
    return getContainingFile();
  }

  @Override
  public String toString() {
    return "SmartPsiFileRangePointerImpl{" + getElementInfo() + "}";
  }
}
