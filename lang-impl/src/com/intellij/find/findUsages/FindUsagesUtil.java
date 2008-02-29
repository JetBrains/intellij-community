
package com.intellij.find.findUsages;

import com.intellij.find.FindManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.psi.PsiElement;

public class FindUsagesUtil {
  private FindUsagesUtil() {
  }

  static boolean isSearchForTextOccurencesAvailable(PsiElement element, boolean isSingleFile) {
    if (isSingleFile) return false;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
    final FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);

    return handler != null && handler.isSearchForTextOccurencesAvailable(element, isSingleFile);
  }
}
