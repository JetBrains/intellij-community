package com.intellij.codeInsight.hint.actions;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;

public class ShowParameterInfoAction extends BaseCodeInsightAction{
  public ShowParameterInfoAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ShowParameterInfoHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file.canContainJavaCode() || file instanceof XmlFile;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}