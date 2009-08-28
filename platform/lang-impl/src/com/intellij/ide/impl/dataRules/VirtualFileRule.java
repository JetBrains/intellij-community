package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

public class VirtualFileRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    // Try to detect multiselection.
    PsiElement[] psiElements = (PsiElement[])dataProvider.getData(DataConstants.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (PsiElement elem : psiElements) {
        VirtualFile virtualFile = PsiUtilBase.getVirtualFile(elem);
        if (virtualFile != null) return virtualFile;
      }
    }

    VirtualFile[] virtualFiles = (VirtualFile[])dataProvider.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length == 1) {
      return virtualFiles[0];
    }

    PsiFile psiFile = (PsiFile)dataProvider.getData(DataConstants.PSI_FILE);
    if (psiFile != null) {
      return psiFile.getVirtualFile();
    }
    PsiElement elem = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (elem == null) {
      return null;
    }
    return PsiUtilBase.getVirtualFile(elem);
  }
}
