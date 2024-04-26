// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public final class VoidConversionRule extends TypeConversionRule {
  @Nullable
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                     PsiType to,
                                                     PsiMember member,
                                                     PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    if (PsiTypes.voidType().equals(to) && context.getParent() instanceof PsiReturnStatement) {
      return new TypeConversionDescriptorBase() {
        @Override
        public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
          PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
          if (method != null) {
            PsiDocComment docComment = method.getDocComment();
            if (docComment != null) {
              PsiDocTag docTag = docComment.findTagByName("return");
              if (docTag != null) {
                docTag.delete();
              }
            }
          }
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiReturnStatement) {
            if (SideEffectChecker.mayHaveSideEffects(expression)) {
              List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(expression);
              PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, expression);
              PsiElement grandParent = parent.getParent();
              for (PsiStatement statement : sideEffectStatements) {
                grandParent.addBefore(statement, parent);
              }
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
            final PsiReturnStatement replaced = (PsiReturnStatement)parent.replace(factory.createStatementFromText("return;", null));
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
