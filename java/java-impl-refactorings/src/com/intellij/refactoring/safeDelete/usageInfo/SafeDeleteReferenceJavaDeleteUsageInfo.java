// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.safeDelete.ImportSearcher;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;


public class SafeDeleteReferenceJavaDeleteUsageInfo extends SafeDeleteReferenceSimpleDeleteUsageInfo {
  private static final Logger LOG = Logger.getInstance(SafeDeleteReferenceJavaDeleteUsageInfo.class);

  public SafeDeleteReferenceJavaDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceJavaDeleteUsageInfo(final PsiElement element,
                                                final PsiElement referencedElement,
                                                final int startOffset,
                                                final int endOffset,
                                                final boolean isNonCodeUsage,
                                                final boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  public SafeDeleteReferenceJavaDeleteUsageInfo(PsiExpression expression, PsiElement referenceElement) {
    this(expression, referenceElement, !RemoveUnusedVariableUtil.checkSideEffects(expression, null, new ArrayList<>()));
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    if (isSafeDelete()) {
      PsiElement element = getElement();
      LOG.assertTrue(element != null);
      PsiElement importStatement = ImportSearcher.getImport(element, false);
      if (importStatement != null) {
        if (element instanceof PsiImportStaticReferenceElement) {
          if (((PsiImportStaticReferenceElement)element).multiResolve(false).length < 2) {
            importStatement.delete();
          }
        } else {
          importStatement.delete();
        }
      }
      else {
        if (element instanceof PsiExpressionStatement &&
            CommonJavaRefactoringUtil.isLoopOrIf(element.getParent()) &&
            !RemoveUnusedVariableUtil.isForLoopUpdate(element)) {
          final PsiStatement emptyTest = JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(";", null);
          element.replace(emptyTest);
        } else {
          element.delete();
        }
      }
    }
  }
}
