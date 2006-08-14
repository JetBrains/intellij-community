package com.intellij.ide.impl.dataRules;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 10, 2004
 */
public class ModuleRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    Object moduleContext = dataProvider.getData(DataConstantsEx.MODULE_CONTEXT);
    if (moduleContext != null) {
      return moduleContext;
    }
    Project project = (Project)dataProvider.getData(DataConstants.PROJECT);
    if (project == null) {
      PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
      if (element == null || !element.isValid()) return null;
      project = element.getProject();
    }

    final PackageElement packageElement = (PackageElement)dataProvider.getData(DataConstantsEx.PACKAGE_ELEMENT);
    if (packageElement != null) {
      return packageElement.getModule();
    }

    VirtualFile virtualFile = (VirtualFile)dataProvider.getData(DataConstants.VIRTUAL_FILE);
    if (virtualFile == null) {
      GetDataRule dataRule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(DataConstants.VIRTUAL_FILE);
      if (dataRule != null) {
        virtualFile = (VirtualFile)dataRule.getData(dataProvider);
      }
    }

    if (virtualFile == null) {
      return null;
    }

    return VfsUtil.getModuleForFile(project, virtualFile);
  }
}
