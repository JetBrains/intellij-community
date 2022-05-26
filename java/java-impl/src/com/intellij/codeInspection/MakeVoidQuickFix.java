// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
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
    return JavaBundle.message("inspection.unused.return.value.make.void.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiMethod psiMethod = null;
    if (myProcessor != null) {
      RefElement refElement = (RefElement)myProcessor.getElement(descriptor);
      if (refElement instanceof RefMethod && refElement.isValid()) {
        psiMethod = (PsiMethod)((RefMethod)refElement).getUastElement().getJavaPsi();
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
    SmartList<PsiMethod> methodsToModify = new SmartList<>(psiMethod);
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      methodsToModify.addAll(OverridingMethodsSearch.search(psiMethod).findAll());
    }, JavaBundle.message("psi.search.overriding.progress"), true, project)) {
      return;
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(methodsToModify)) return;
    var provider = JavaSpecialRefactoringProvider.getInstance();
    var csp = provider.getChangeSignatureProcessorWithCallback(project,
                                                                     psiMethod,
                                                                     false, null, psiMethod.getName(),
                                                                     PsiType.VOID,
                                                                     ParameterInfoImpl.fromMethod(psiMethod),
                                                                     true,
                                                                     infos -> {
                                                                       for (final PsiMethod method : methodsToModify) {
                                                                         replaceReturnStatements(method);
                                                                       }
                                                                     });
    csp.run();
  }

  private static void replaceReturnStatements(@NotNull final PsiMethod method) {
    final PsiReturnStatement[] statements = PsiUtil.findReturnStatements(method);
    if (statements.length > 0) {
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
              if (UnnecessaryReturnInspection.isReturnRedundant(returnStatement, false, true, null)) {
                returnStatement.delete();
              }
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }
}
