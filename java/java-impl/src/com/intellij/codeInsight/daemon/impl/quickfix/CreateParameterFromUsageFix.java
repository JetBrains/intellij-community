/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
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

    final Project project = myReferenceExpression.getProject();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);
    method = IntroduceParameterHandler.chooseEnclosingMethod(method);
    if (method == null) return;

    method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (method == null) return;

    List<ParameterInfoImpl> parameterInfos = new ArrayList<ParameterInfoImpl>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
    ParameterInfoImpl parameterInfo = new ParameterInfoImpl(-1, varName, type, PsiTypesUtil.getDefaultValueOfType(type), false);
    if (!method.isVarArgs()) {
      parameterInfos.add(parameterInfo);
    } else {
      parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[parameterInfos.size()]);
      @Modifier String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(method.getModifierList()));
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(project, method, false, modifier, method.getName(), method.getReturnType(), array);
      processor.run();
    }
    else {
      JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(project, method, false, myReferenceExpression);
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
