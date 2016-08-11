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
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.NotNullFunction;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Danila Ponomarenko
 */
public class ParameterCanBeLocalInspection extends ParameterCanBeLocalInspectionBase {
  @Override
  protected ConvertParameterToLocalQuickFix createFix() {
    return new ConvertParameterToLocalQuickFix();
  }

  public static class ConvertParameterToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiParameter> {
    @Override
    protected PsiParameter getVariable(@NotNull ProblemDescriptor descriptor) {
      return (PsiParameter)descriptor.getPsiElement().getParent();
    }

    @Override
    protected PsiElement applyChanges(@NotNull final Project project,
                                      @NotNull final String localName,
                                      @Nullable final PsiExpression initializer,
                                      @NotNull final PsiParameter parameter,
                                      @NotNull final Collection<PsiReference> references,
                                      boolean delete, 
                                      @NotNull final NotNullFunction<PsiDeclarationStatement, PsiElement> action) {
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        final PsiParameter[] parameters = method.getParameterList().getParameters();

        final List<ParameterInfoImpl> info = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter psiParameter = parameters[i];
          if (psiParameter == parameter) continue;
          info.add(new ParameterInfoImpl(i, psiParameter.getName(), psiParameter.getType()));
        }
        final ParameterInfoImpl[] newParams = info.toArray(new ParameterInfoImpl[info.size()]);
        final String visibilityModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
        final PsiType returnType = method.getReturnType();
        final JavaChangeInfo changeInfo = new JavaChangeInfoImpl(visibilityModifier, method, method.getName(),
                                                                 returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                                                 newParams, null, false, ContainerUtil.newHashSet(), ContainerUtil.newHashSet());
        final ChangeSignatureProcessor cp = new ChangeSignatureProcessor(project, changeInfo) {
          @Override
          protected void performRefactoring(@NotNull UsageInfo[] usages) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            final PsiElement newDeclaration = moveDeclaration(elementFactory, localName, parameter, initializer, action, references);
            super.performRefactoring(usages);
            positionCaretToDeclaration(project, newDeclaration.getContainingFile(), newDeclaration);
          }
        };
        cp.run();
      }
      return null;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiParameter parameter, @NotNull PsiCodeBlock scope) {
      return parameter.getName();
    }
  }
}
