/*
* Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.LangBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HighlightMessageUtil {
  private HighlightMessageUtil() { }

  @Nullable
  public static String getSymbolName(@NotNull PsiElement symbol, PsiSubstitutor substitutor) {
    String symbolName = null;

    if (symbol instanceof PsiClass) {
      if (symbol instanceof PsiAnonymousClass) {
        symbolName = LangBundle.message("java.terms.anonymous.class");
      }
      else {
        symbolName = ((PsiClass)symbol).getQualifiedName();
        if (symbolName == null) {
          symbolName = ((PsiClass)symbol).getName();
        }
      }
    }
    else if (symbol instanceof PsiMethod) {
      symbolName = PsiFormatUtil.formatMethod((PsiMethod)symbol,
                                              substitutor, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                              PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES | PsiFormatUtilBase.USE_INTERNAL_CANONICAL_TEXT);
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

    return symbolName;
  }
}
