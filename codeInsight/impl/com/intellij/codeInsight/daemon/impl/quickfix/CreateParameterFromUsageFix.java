package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Mike
 */
public class CreateParameterFromUsageFix extends CreateVarFromUsageFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateParameterFromUsageFix");

  public CreateParameterFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(myReferenceExpression.isQualified()) return false;
    PsiElement scope = myReferenceExpression;
    do {
      scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
      if (!(scope instanceof PsiAnonymousClass)) {
        return scope instanceof PsiMethod &&
               ((PsiMethod)scope).getParameterList().isPhysical();
      }
    }
    while (true);
  }

    public String getText(String varName) {
    return QuickFixBundle.message("create.parameter.from.usage.text", varName);
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, true)) return;

    PsiManager psiManager = myReferenceExpression.getManager();
    Project project = psiManager.getProject();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);

    method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (method == null) return;

    List<ParameterInfo> parameterInfos = new ArrayList<ParameterInfo>(Arrays.asList(ParameterInfo.fromMethod(method)));
    ParameterInfo parameterInfo = new ParameterInfo(-1, varName, type, PsiTypesUtil.getDefaultValueOfType(type), true);
    parameterInfos.add(parameterInfo);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ParameterInfo[] array = parameterInfos.toArray(new ParameterInfo[parameterInfos.size()]);
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(project, method, false, PsiUtil.getAccessModifier(
        PsiUtil.getAccessLevel(method.getModifierList())), method.getName(), method.getReturnType(), array);
      processor.run();
    }
    else {
      ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, false, myReferenceExpression);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
    }
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }

}
