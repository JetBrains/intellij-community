// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class CastCanBeRemovedNarrowingVariableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTypeCastExpression(@NotNull PsiTypeCastExpression cast) {
        PsiTypeElement castTypeElement = cast.getCastType();
        if (castTypeElement == null || castTypeElement.getAnnotations().length > 0) return;
        PsiType castType = GenericsUtil.getVariableTypeByExpressionType(cast.getType());
        if (!(castType instanceof PsiClassType) || ((PsiClassType)castType).isRaw()) return;
        PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(cast.getOperand()), PsiReferenceExpression.class);
        if (ref == null) return;
        PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
        if (!PsiUtil.isJvmLocalVariable(variable)) return;
        if (variable instanceof PsiParameter parameter) {
          PsiForeachStatement forEach = tryCast(parameter.getDeclarationScope(), PsiForeachStatement.class);
          if (forEach == null) return;
          PsiExpression collection = forEach.getIteratedValue();
          if (collection == null) return;
          PsiType elementType = JavaGenericsUtil.getCollectionItemType(collection);
          if (elementType == null || !castType.isAssignableFrom(elementType)) return;
          PsiType elementVarType = GenericsUtil.getVariableTypeByExpressionType(elementType);
          if (elementVarType instanceof PsiClassType elementClassType && elementClassType.isRaw()) return;
        } else {
          PsiExpression variableInitializer = variable.getInitializer();
          if (variableInitializer != null) {
            PsiType initializerType = GenericsUtil.getVariableTypeByExpressionType(variableInitializer.getType());
            if (initializerType == null || !castType.isAssignableFrom(initializerType)) return;
          }
        }
        if (!InstanceOfUtils.isSafeToNarrowType(variable, cast, castType)) return;
        String message = JavaBundle
          .message("inspection.cast.can.be.removed.narrowing.variable.type.message", variable.getName(), castType.getPresentableText());
        holder.registerProblem(castTypeElement, message, new CastCanBeRemovedNarrowingVariableTypeFix(variable, castType, isOnTheFly));
      }
    };
  }

  private static class CastCanBeRemovedNarrowingVariableTypeFix extends PsiUpdateModCommandQuickFix {
    private final String myVariableName;
    private final String myType;
    private final boolean myOnTheFly;

    CastCanBeRemovedNarrowingVariableTypeFix(@NotNull PsiVariable variable, @NotNull PsiType type, boolean onTheFly) {
      myVariableName = variable.getName();
      myType = type.getPresentableText();
      myOnTheFly = onTheFly;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.cast.can.be.removed.narrowing.variable.type.fix.name", myVariableName, myType);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.cast.can.be.removed.narrowing.variable.type.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiTypeCastExpression cast = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
      if (cast == null) return;
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(cast.getOperand()), PsiReferenceExpression.class);
      if (ref == null) return;
      PsiVariable var = tryCast(ref.resolve(), PsiVariable.class);
      if (var == null) return;
      PsiTypeElement castType = cast.getCastType();
      if (castType == null) return;
      var.normalizeDeclaration();
      PsiTypeElement typeElement = var.getTypeElement();
      if (typeElement == null) return;
      PsiElement newTypeElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(typeElement.replace(castType));
      for (PsiReference reference : ReferencesSearch.search(var).findAll()) {
        if (reference instanceof PsiReferenceExpression varRef) {
          PsiTypeCastExpression castOccurrence =
            tryCast(PsiUtil.skipParenthesizedExprUp(varRef.getParent()), PsiTypeCastExpression.class);
          if (castOccurrence != null && RedundantCastUtil.isCastRedundant(castOccurrence)) {
            RemoveRedundantCastUtil.removeCast(castOccurrence);
          }
        }
      }
      if (myOnTheFly) {
        updater.highlight(newTypeElement, EditorColors.SEARCH_RESULT_ATTRIBUTES);
      }
    }
  }
}
