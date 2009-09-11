package com.intellij.refactoring.copy;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;

/**
 * @author yole
 */
public class JavaCopyFilesOrDirectoriesHandler extends CopyFilesOrDirectoriesHandler {
  protected boolean canCopyFiles(final PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) ||
          element instanceof PsiClassOwner &&
          PsiUtilBase.getTemplateLanguageFile(element) != element &&
          !CollectHighlightsUtil.isOutsideSourceRoot((PsiFile) element)) {
        return false;
      }
    }

    return super.canCopyFiles(elements);
  }

  protected boolean canCopyDirectories(final PsiElement[] elements) {
    if (!super.canCopyDirectories(elements)) return false;

    for (PsiElement element1 : elements) {
      PsiDirectory directory = (PsiDirectory)element1;

      if (hasPackages(directory)) {
        return false;
      }
    }
    return true;
  }

  public static boolean hasPackages(PsiDirectory directory) {
    if (JavaDirectoryService.getInstance().getPackage(directory) != null) {
      return true;
    }
    return false;
  }
}
