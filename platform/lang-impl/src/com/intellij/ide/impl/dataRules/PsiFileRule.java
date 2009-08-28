package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;

public class PsiFileRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (element != null){
      return element.getContainingFile();
    }
    Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
    if (project != null){
      VirtualFile vFile = (VirtualFile)dataProvider.getData(DataConstants.VIRTUAL_FILE);
      if (vFile != null){
        return PsiManager.getInstance(project).findFile(vFile);
      }
    }
    return null;
  }
}
