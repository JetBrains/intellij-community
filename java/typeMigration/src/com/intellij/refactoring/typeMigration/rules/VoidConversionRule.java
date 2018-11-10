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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.SideEffectChecker;
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
    if (PsiType.VOID.equals(to) &&
        context.getParent() instanceof PsiReturnStatement &&
        !SideEffectChecker.mayHaveSideEffects(context)) {
      return new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
          final PsiElement parent = expression.getParent();
          final Project project = expression.getProject();
          if (parent instanceof PsiReturnStatement) {
            final PsiReturnStatement replaced = (PsiReturnStatement)parent.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText("return;", null));
            if (UnnecessaryReturnInspection.isReturnRedundant(replaced, false, false, null)) {
              DeleteUnnecessaryStatementFix.deleteUnnecessaryStatement(replaced);
            }
          }
          return null;
        }
      };
    }
    return null;
  }
}
