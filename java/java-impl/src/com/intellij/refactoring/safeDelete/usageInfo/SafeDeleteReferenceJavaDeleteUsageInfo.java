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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.safeDelete.ImportSearcher;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

/**
 * @author yole
 */
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
            RefactoringUtil.isLoopOrIf(element.getParent()) &&
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
