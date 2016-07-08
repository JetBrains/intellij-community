/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class VoidConversionRule extends TypeConversionRule {
  @Nullable
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                     PsiType to,
                                                     PsiMember member,
                                                     PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    if (PsiType.VOID.equals(to) && context.getParent() instanceof PsiReturnStatement) {
      final boolean isPure = PsiTreeUtil.processElements(context, new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull PsiElement element) {
          if (element instanceof PsiPrefixExpression) {
            return analyzeUnaryExpressionOperand(((PsiPrefixExpression)element).getOperand());
          }
          if (element instanceof PsiPostfixExpression) {
            return analyzeUnaryExpressionOperand(((PsiPostfixExpression)element).getOperand());
          }
          if (element instanceof PsiMethodCallExpression) {
            final PsiMethod method = ((PsiMethodCallExpression)element).resolveMethod();
            return method != null && ControlFlowAnalyzer.isPure(method);
          }
          return true;
        }

        private boolean analyzeUnaryExpressionOperand(PsiExpression operand) {
          if (!(operand instanceof PsiReferenceExpression)) return false;
          final PsiElement resolved = ((PsiReferenceExpression)operand).resolve();
          return !(resolved instanceof PsiField);
        }
      });
      if (isPure) {
        return new TypeConversionDescriptorBase() {
          @Override
          public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiReturnStatement) {
              expression.delete();
              if (UnnecessaryReturnInspection.isReturnRedundant((PsiReturnStatement)parent, false, null)) {
                parent.delete();
              }
            }
            return null;
          }
        };
      } else {
        return null;
      }
    }
    return null;
  }
}
