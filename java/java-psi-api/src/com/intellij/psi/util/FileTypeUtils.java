// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ServerPageFile;

public final class FileTypeUtils {
  public static boolean isInServerPageFile(PsiElement file) {
    return PsiUtilCore.getTemplateLanguageFile(file) instanceof ServerPageFile;
  }
}
