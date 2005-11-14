package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class VirtualFileRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    // Try to detect multiselection.

    PsiElement[] psiElements = (PsiElement[])dataProvider.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (int i = 0; i < psiElements.length; i++) {
        PsiElement elem = psiElements[i];
        if (elem instanceof PsiDirectory) {
          VirtualFile file = ((PsiDirectory)elem).getVirtualFile();
          return file;
        }
        else {
          PsiFile containingFile = elem.getContainingFile();
          if (containingFile != null) {
            VirtualFile file = containingFile.getVirtualFile();
            return file;
          }
        }
      }
    }


    VirtualFile[] virtualFiles = (VirtualFile[])dataProvider.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length == 1) {
      return virtualFiles[0];
    }

    //

    PsiFile psiFile = (PsiFile)dataProvider.getData(DataConstants.PSI_FILE);
    if (psiFile != null) {
      return psiFile.getVirtualFile();
    }
    PsiElement elem = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (elem == null) {
      return null;
    }
    if (elem instanceof PsiFile) {
      return ((PsiFile)elem).getVirtualFile();
    }
    else if (elem instanceof PsiDirectory) {
      return ((PsiDirectory)elem).getVirtualFile();
    }
    else {
      PsiFile file = elem.getContainingFile();
      return file != null ? file.getVirtualFile() : null;
    }
  }
}
