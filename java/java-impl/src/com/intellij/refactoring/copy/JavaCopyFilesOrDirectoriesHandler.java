package com.intellij.refactoring.copy;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;

/**
 * @author yole
 */
public class JavaCopyFilesOrDirectoriesHandler extends CopyFilesOrDirectoriesHandler {
  @Override
  protected boolean canCopyFile(final PsiFile element) {
    if (element instanceof PsiClassOwner &&
        PsiUtilBase.getTemplateLanguageFile(element) != element &&
        !CollectHighlightsUtil.isOutsideSourceRoot(element)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canCopyDirectory(PsiDirectory element) {
    return !hasPackages(element);
  }

  public static boolean hasPackages(PsiDirectory directory) {
    if (JavaDirectoryService.getInstance().getPackage(directory) != null) {
      return true;
    }
    return false;
  }
}
