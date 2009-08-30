/*
 * User: anna
 * Date: 15-Jan-2008
 */
package com.intellij.analysis;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;

public class AnalysisScopeRule implements GetDataRule {
  public Object getData(final DataProvider dataProvider) {
    final Object psiFile = dataProvider.getData(LangDataKeys.PSI_FILE.getName());
    if (psiFile instanceof PsiJavaFile) {
      return new JavaAnalysisScope((PsiJavaFile)psiFile);
    }
    Object psiTarget = dataProvider.getData(LangDataKeys.PSI_ELEMENT.getName());
    if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      if (!pack.getManager().isInProject(pack)) return null;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs.length == 0) return null;
      return new JavaAnalysisScope(pack, (Module)dataProvider.getData(LangDataKeys.MODULE.getName()));
    }
    return null;
  }
}