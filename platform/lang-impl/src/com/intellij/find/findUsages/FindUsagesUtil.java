
package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;

public class FindUsagesUtil {
  private FindUsagesUtil() {
  }

  static boolean isSearchForTextOccurencesAvailable(PsiElement element, boolean isSingleFile, FindUsagesHandler handler) {
    if (isSingleFile) return false;

    return handler != null && handler.isSearchForTextOccurencesAvailable(element, isSingleFile);
  }
}
