/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MakeVoidQuickFix implements LocalQuickFix {
  private final ProblemDescriptionsProcessor myProcessor;
  private static final Logger LOG = Logger.getInstance(MakeVoidQuickFix.class);

  public MakeVoidQuickFix(@Nullable final ProblemDescriptionsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiMethod psiMethod = null;
    if (myProcessor != null) {
      RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
      if (refElement instanceof RefMethod && refElement.isValid()) {
        RefMethod refMethod = (RefMethod)refElement;
        psiMethod = (PsiMethod)refMethod.getElement();
      }
    }
    else {
      psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
    }
    if (psiMethod == null) return;
    makeMethodHierarchyVoid(project, psiMethod);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  private static void makeMethodHierarchyVoid(Project project, @NotNull PsiMethod psiMethod) {
    replaceReturnStatements(psiMethod);
    for (final PsiMethod oMethod : OverridingMethodsSearch.search(psiMethod)) {
      replaceReturnStatements(oMethod);
    }
    final PsiParameter[] params = psiMethod.getParameterList().getParameters();
    final ParameterInfoImpl[] infos = new ParameterInfoImpl[params.length];
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      infos[i] = new ParameterInfoImpl(i, param.getName(), param.getType());
    }

    final ChangeSignatureProcessor csp = new ChangeSignatureProcessor(project,
                                                                      psiMethod,
                                                                      false, null, psiMethod.getName(),
                                                                      PsiType.VOID,
                                                                      infos);

    csp.run();
  }

  private static void replaceReturnStatements(@NotNull final PsiMethod method) {
    final PsiReturnStatement[] statements = PsiUtil.findReturnStatements(method);
    if (statements.length > 0) {
      WriteAction.run(() -> {
        for (int i = statements.length - 1; i >= 0; i--) {
          PsiReturnStatement returnStatement = statements[i];
          try {
            final PsiExpression expression = returnStatement.getReturnValue();
            if (expression != null) {
              List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(expression);
              PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, expression);
              if (sideEffectStatements.length > 0) {
                PsiStatement added = BlockUtils.addBefore(returnStatement, sideEffectStatements);
                returnStatement = PsiTreeUtil.getNextSiblingOfType(added, PsiReturnStatement.class);
              }
              if (returnStatement != null && returnStatement.getReturnValue() != null) {
                returnStatement.getReturnValue().delete();
                if (UnnecessaryReturnInspection.isReturnRedundant(returnStatement, false, null)) {
                  returnStatement.delete();
                }
              }
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
    }
  }
}
