// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FileContextUtil {
  public static final Key<SmartPsiElementPointer> INJECTED_IN_ELEMENT = Key.create("injectedIn");
  public static final Key<PsiFile> CONTAINING_FILE_KEY = Key.create("CONTAINING_FILE_KEY");

  private FileContextUtil() { }

  @Nullable
  public static PsiElement getFileContext(@NotNull PsiFile file) {
    SmartPsiElementPointer pointer = file.getUserData(INJECTED_IN_ELEMENT);
    return pointer == null ? null : pointer.getElement();
  }

  @Nullable
  public static PsiFile getContextFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    PsiElement context = file.getContext();
    if (context == null) {
      return file;
    }
    else {
      return getContextFile(context);
    }
  }
}