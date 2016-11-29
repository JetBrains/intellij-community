/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
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

  @Override
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

    @Override
    public String getText(String varName) {
    return QuickFixBundle.message("create.parameter.from.usage.text", varName);
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) return;

    final Project project = myReferenceExpression.getProject();

    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    final String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);
    method = IntroduceParameterHandler.chooseEnclosingMethod(method);
    if (method == null) return;

    method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (method == null) return;

    final List<ParameterInfoImpl> parameterInfos =
      new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
    ParameterInfoImpl parameterInfo = new ParameterInfoImpl(-1, varName, type, varName, false);
    if (!method.isVarArgs()) {
      parameterInfos.add(parameterInfo);
    }
    else {
      parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
    }

    final Application application = ApplicationManager.getApplication();
    final PsiMethod finalMethod = method;
    application.invokeLater(() -> {
      if (project.isDisposed()) return;
      if (application.isUnitTestMode()) {
        ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[parameterInfos.size()]);
        String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(finalMethod.getModifierList()));
        ChangeSignatureProcessor processor =
          new ChangeSignatureProcessor(project, finalMethod, false, modifier, finalMethod.getName(), finalMethod.getReturnType(), array);
        processor.run();
      }
      else {
        try {
          JavaChangeSignatureDialog dialog =
            JavaChangeSignatureDialog.createAndPreselectNew(project, finalMethod, parameterInfos, true, myReferenceExpression);
          dialog.setParameterInfos(parameterInfos);
          if (dialog.showAndGet()) {
            for (ParameterInfoImpl info : parameterInfos) {
              if (info.getOldIndex() == -1) {
                final String newParamName = info.getName();
                if (!Comparing.strEqual(varName, newParamName)) {
                  final PsiExpression newExpr =
                    JavaPsiFacade.getElementFactory(project).createExpressionFromText(newParamName, finalMethod);
                  new WriteCommandAction(project) {
                    @Override
                    protected void run(@NotNull Result result) throws Throwable {
                      final PsiReferenceExpression[] refs =
                        CreateFromUsageUtils.collectExpressions(myReferenceExpression, PsiMember.class, PsiFile.class);
                      for (PsiReferenceExpression ref : refs) {
                        ref.replace(newExpr.copy());
                      }
                    }
                  }.execute();
                }
                break;
              }
            }
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }

}
