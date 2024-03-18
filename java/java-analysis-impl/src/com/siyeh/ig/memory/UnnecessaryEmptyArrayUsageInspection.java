// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.memory;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ConstructionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class UnnecessaryEmptyArrayUsageInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("constant.for.zero.length.array.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ReplaceEmptyArrayToConstantFix((PsiClass)infos[0], (PsiField)infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        if (ConstructionUtils.isEmptyArrayInitializer(expression)) {
          PsiType type = expression.getType();
          if (type instanceof PsiArrayType) {
            PsiType arrayType = ((PsiArrayType)type).getComponentType();
            PsiClass typeClass = PsiTypesUtil.getPsiClass(arrayType);
            if (typeClass != null) {
              for (PsiField field : typeClass.getFields()) {
                PsiModifierList modifiers = field.getModifierList();
                if (modifiers != null
                    && !typeClass.isEquivalentTo(PsiTreeUtil.findFirstParent(expression, e -> e instanceof PsiClass))
                    && modifiers.hasModifierProperty(PsiModifier.PUBLIC)
                    && field.getType().equals(type)
                    && CollectionUtils.isConstantEmptyArray(field)) {
                  registerError(expression, typeClass, field);
                  return;
                }
              }
            }
          }
        }
        super.visitNewExpression(expression);
      }
    };
  }
}
