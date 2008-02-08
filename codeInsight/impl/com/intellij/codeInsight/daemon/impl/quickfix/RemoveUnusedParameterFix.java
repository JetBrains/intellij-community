package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedParameterFix implements IntentionAction {
  private final PsiParameter myParameter;

  public RemoveUnusedParameterFix(PsiParameter parameter) {
    myParameter = parameter;
  }

  public String getText() {
    return QuickFixBundle.message("remove.unused.parameter.text", myParameter.getName());
  }

  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.parameter.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
      myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && myParameter.getManager().isInProject(myParameter);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  private static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(parameter.getProject(),
                                                                      method,
        false, null,
        method.getName(),
        method.getReturnType(),
        getNewParametersInfo(method, parameter));

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    }
    else {
      processor.run();
    }
  }

  public static ParameterInfo[] getNewParametersInfo(PsiMethod method, PsiParameter parameterToRemove) {
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (!Comparing.equal(parameter, parameterToRemove)) {
        result.add(new ParameterInfo(i, parameter.getName(), parameter.getType()));
      }
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }


}
