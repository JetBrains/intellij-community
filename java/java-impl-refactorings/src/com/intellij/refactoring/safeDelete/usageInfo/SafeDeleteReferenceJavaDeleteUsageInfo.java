// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiStatement;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.safeDelete.ImportSearcher;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      deleteReference(element);
    }
  }

  /**
   * Deletes the import that holds {@code element}, unless a static import also imports another member; else deletes the element, or
   * replaces it with an empty statement where a statement must stay.
   */
  public static void deleteReference(@NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement importStatement = ImportSearcher.getImport(element, false);
    if (importStatement != null) {
      if (element instanceof PsiImportStaticReferenceElement importStaticReferenceElement) {
        if (importStaticReferenceElement.multiResolve(false).length < 2) {
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
        final PsiStatement emptyTest = JavaPsiFacade.getElementFactory(element.getProject()).createStatementFromText(";", null);
        element.replace(emptyTest);
      } else {
        element.delete();
      }
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiElement element = getElement();
    if (!isSafeDelete() || element == null) return null;
    return ModPlanEdit.update(element, SafeDeleteReferenceJavaDeleteUsageInfo::deleteReference);
  }
}
