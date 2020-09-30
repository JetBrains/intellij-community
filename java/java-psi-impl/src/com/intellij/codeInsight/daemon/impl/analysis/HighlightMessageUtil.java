// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HighlightMessageUtil {
  private HighlightMessageUtil() { }

  @Nullable
  public static String getSymbolName(@NotNull PsiElement symbol) {
    return getSymbolName(symbol, PsiSubstitutor.EMPTY);
  }

  @Nullable
  public static String getSymbolName(@NotNull PsiElement symbol, @NotNull PsiSubstitutor substitutor) {
    int options = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT;
    return getSymbolName(symbol, substitutor, options);
  }

  @Nullable
  public static String getSymbolName(@NotNull PsiElement symbol, @NotNull PsiSubstitutor substitutor, int parameterOptions) {
    String symbolName = null;

    if (symbol instanceof PsiClass) {
      if (symbol instanceof PsiAnonymousClass) {
        symbolName = JavaPsiBundle.message("java.terms.anonymous.class");
      }
      else {
        symbolName = ((PsiClass)symbol).getQualifiedName();
        if (symbolName == null) {
          symbolName = ((PsiClass)symbol).getName();
        }
      }
    }
    else if (symbol instanceof PsiMethod) {
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
      symbolName = PsiFormatUtil.formatMethod((PsiMethod)symbol, substitutor, options, parameterOptions);
    }
    else if (symbol instanceof PsiVariable) {
      symbolName = ((PsiVariable)symbol).getName();
    }
    else if (symbol instanceof PsiPackage) {
      symbolName = ((PsiPackage)symbol).getQualifiedName();
    }
    else if (symbol instanceof PsiFile) {
      PsiDirectory directory = ((PsiFile)symbol).getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
      symbolName = aPackage == null ? null : aPackage.getQualifiedName();
    }
    else if (symbol instanceof PsiDirectory) {
      symbolName = ((PsiDirectory)symbol).getName();
    }
    else if (symbol instanceof PsiJavaModule) {
      symbolName = ((PsiJavaModule)symbol).getName();
    }

    return symbolName;
  }
}