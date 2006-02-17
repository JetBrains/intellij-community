package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.ImplementMethodsHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

/**
 *
 */
public class ImplementMethodsAction extends BaseCodeInsightAction {

  protected CodeInsightActionHandler getHandler() {
    return new ImplementMethodsHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null) {
      return false;
    }
    return OverrideImplementUtil.getMethodSignaturesToImplement(aClass).length != 0;
    //final Collection<HierarchicalMethodSignature> allMethods = aClass.getVisibleSignatures();
    //return ClassUtil.getAnyMethodToImplement(aClass, allMethods) != null;
  }
}