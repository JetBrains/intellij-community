package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class MethodParameterFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnFix");

  private final PsiMethod myMethod;
  private final PsiType myParameterType;
  private final int myIndex;
  private final boolean myFixWholeHierarchy;

  public MethodParameterFix(PsiMethod method, PsiType type, int index, boolean fixWholeHierarchy) {
    myMethod = method;
    myParameterType = type;
    myIndex = index;
    myFixWholeHierarchy = fixWholeHierarchy;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("fix.parameter.type.text",
                                  myMethod.getName(),
                                  myParameterType.getCanonicalText() );
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.parameter.type.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myParameterType != null
        && !TypeConversionUtil.isNullType(myParameterType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myParameterType, myMethod.getReturnType());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    try {
      PsiMethod method = myMethod;
      if (myFixWholeHierarchy) {
        method = myMethod.findDeepestSuperMethod();
        if (method == null) method = myMethod;
      }

      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(project,
                                                                        method,
                                                                        false, null,
                                                                        method.getName(),
                                                                        method.getReturnType(),
                                                                        getNewParametersInfo());

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        processor.run();
      }
      else {
        processor.run();
      }


      UndoUtil.markPsiFileForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private ParameterInfo[] getNewParametersInfo() throws IncorrectOperationException {
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myMethod.getProject());
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, myParameterType);
    PsiParameter newParameter = factory.createParameter(nameInfo.names[0], myParameterType);

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (i == myIndex) {
        newParameter.setName(parameter.getName());
        parameter = newParameter;
      }
      result.add(new ParameterInfo(i, parameter.getName(), parameter.getType()));
    }
    if (parameters.length == myIndex) {
      result.add(new ParameterInfo(-1, newParameter.getName(), newParameter.getType()));
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
