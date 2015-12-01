/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
class SmartPsiFileRangePointerImpl extends SmartPsiElementPointerImpl<PsiFile> implements SmartPsiFileRange {
  SmartPsiFileRangePointerImpl(@NotNull PsiFile containingFile, @NotNull ProperTextRange range, boolean forInjected) {
    super(containingFile, createElementInfo(containingFile, range, forInjected), PsiFile.class);
  }

  @NotNull
  private static SmartPointerElementInfo createElementInfo(@NotNull PsiFile containingFile, @NotNull ProperTextRange range, boolean forInjected) {
    Project project = containingFile.getProject();
    if (containingFile.getViewProvider() instanceof FreeThreadedFileViewProvider) {
      PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(containingFile);
      if (host != null) {
        SmartPsiElementPointer<PsiLanguageInjectionHost> hostPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(host);
        return new InjectedSelfElementInfo(project, containingFile, range, containingFile, hostPointer);
      }
    }
    if (range.equals(containingFile.getTextRange())) return new FileElementInfo(containingFile);
    return new SelfElementInfo(project, range, AnchorTypeInfo.obtainInfo(PsiElement.class, null, LanguageUtil.getRootLanguage(containingFile)), containingFile, forInjected);
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
