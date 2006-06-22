
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.lang.LangBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

public class HighlightMessageUtil {
  public static String getSymbolName(@NotNull PsiElement symbol, PsiSubstitutor substitutor) {
    String symbolName = null;
    if (symbol instanceof PsiClass) {
      if (symbol instanceof PsiAnonymousClass){
        symbolName = LangBundle.message("java.terms.anonymous.class");
      }
      else{
        symbolName = ((PsiClass)symbol).getQualifiedName();
        if (symbolName == null) {
          symbolName = ((PsiClass)symbol).getName();
        }
      }
    }
    else if (symbol instanceof PsiMethod) {
      symbolName = PsiFormatUtil.formatMethod((PsiMethod)symbol,
                                              substitutor, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                              PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES
      );
    }
    else if (symbol instanceof PsiVariable) {
      symbolName = ((PsiVariable)symbol).getName();
    }
    else if (symbol instanceof PsiPackage) {
      symbolName = ((PsiPackage)symbol).getQualifiedName();
    }
    else if (symbol instanceof PsiJavaFile) {
      PsiDirectory directory = ((PsiJavaFile) symbol).getContainingDirectory();
      PsiPackage aPackage = directory == null ? null : directory.getPackage();
      symbolName = aPackage == null ? null : aPackage.getQualifiedName();
    }
    else if (symbol instanceof PsiDirectory){
      symbolName = ((PsiDirectory) symbol).getName();
    }
    return symbolName;
  }
}
