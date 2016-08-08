/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class SameParameterValueInspection extends SameParameterValueInspectionBase {
  private static final Logger LOG = Logger.getInstance("#" + SameParameterValueInspectionBase.class.getName());

  protected LocalQuickFix createFix(String paramName, String value) {
    return new InlineParameterValueFix(paramName, value);
  }

  public static class InlineParameterValueFix implements LocalQuickFix {
    private final String myValue;
    private final String myParameterName;

    public InlineParameterValueFix(final String parameterName, final String value) {
      myValue = value;
      myParameterName = parameterName;
    }

    @Override
    public String toString() {
      return getParamName() + " " + getValue();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.same.parameter.fix.name", myParameterName, myValue);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.same.parameter.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method == null) return;
      PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
      if (parameter == null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter psiParameter : parameters) {
          if (Comparing.strEqual(psiParameter.getName(), myParameterName)) {
            parameter = psiParameter;
            break;
          }
        }
      }
      if (parameter == null) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, parameter)) return;

      final PsiExpression defToInline;
      try {
        defToInline = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(myValue, parameter);
      }
      catch (IncorrectOperationException e) {
        return;
      }
      final PsiParameter parameterToInline = parameter;
      inlineSameParameterValue(method, parameterToInline, defToInline);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    public static void inlineSameParameterValue(final PsiMethod method, final PsiParameter parameter, final PsiExpression defToInline) {
      final Collection<PsiReference> refsToInline = ReferencesSearch.search(parameter).findAll();

      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
          int idx = 0;
          for (PsiReference reference : refsToInline) {
            if (reference instanceof PsiJavaCodeReferenceElement) {
              exprs[idx++] = InlineUtil.inlineVariable(parameter, defToInline, (PsiJavaCodeReferenceElement)reference);
            }
          }

          for (final PsiExpression expr : exprs) {
            if (expr != null) InlineUtil.tryToInlineArrayCreationForVarargs(expr);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      });

      removeParameter(method, parameter);
    }

    public static void removeParameter(final PsiMethod method, final PsiParameter parameter) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final List<ParameterInfoImpl> psiParameters = new ArrayList<>();
      int paramIdx = 0;
      final String paramName = parameter.getName();
      for (PsiParameter param : parameters) {
        if (!Comparing.strEqual(paramName, param.getName())) {
          psiParameters.add(new ParameterInfoImpl(paramIdx, param.getName(), param.getType()));
        }
        paramIdx++;
      }

      new ChangeSignatureProcessor(method.getProject(), method, false, null, method.getName(), method.getReturnType(),
                                   psiParameters.toArray(new ParameterInfoImpl[psiParameters.size()])).run();
    }

    public String getValue() {
      return myValue;
    }

    public String getParamName() {
      return myParameterName;
    }
  }
}
