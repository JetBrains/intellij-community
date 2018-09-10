// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RedundantExplicitVariableTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_10)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (!typeElement.isInferredType()) {
          PsiElement parent = variable.getParent();
          if (parent instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)parent).getDeclaredElements().length > 1) {
            return;
          }
          doCheck(variable, (PsiLocalVariable)variable.copy(), typeElement);
        }
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        PsiParameter parameter = statement.getIterationParameter();
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null && !typeElement.isInferredType()) {
          PsiForeachStatement copy = (PsiForeachStatement)statement.copy();
          doCheck(parameter, copy.getIterationParameter(), typeElement);
        }
      }

       private void doCheck(PsiVariable variable,
                            PsiVariable copyVariable,
                            PsiTypeElement element2Highlight) {
         PsiTypeElement typeElementCopy = copyVariable.getTypeElement();
         if (typeElementCopy != null) {
           replaceExplicitTypeWithVar(typeElementCopy, variable);
           if (variable.getType().equals(copyVariable.getType())) {
             holder.registerProblem(element2Highlight,
                                    "Explicit type of local variable can be omitted",
                                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                    new ReplaceWithVarFix());
           }
         }
       }
    };
  }

  private static PsiElement replaceExplicitTypeWithVar(PsiTypeElement typeElement, PsiElement context) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiVariable) {
      PsiExpression copyVariableInitializer = ((PsiVariable)parent).getInitializer();
      if (copyVariableInitializer instanceof PsiNewExpression) {
        final PsiDiamondType.DiamondInferenceResult diamondResolveResult =
          PsiDiamondTypeImpl.resolveInferredTypesNoCheck((PsiNewExpression)copyVariableInitializer, copyVariableInitializer);
        if (!diamondResolveResult.getInferredTypes().isEmpty()) {
          PsiDiamondTypeUtil.expandTopLevelDiamondsInside(copyVariableInitializer);
        }
      }
    }

    return typeElement.replace(JavaPsiFacade.getElementFactory(context.getProject()).createTypeElementFromText("var", context));
  }

  private static class ReplaceWithVarFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace explicit type with 'var'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiTypeElement) {
        CodeStyleManager.getInstance(project)
          .reformat(replaceExplicitTypeWithVar((PsiTypeElement)element, element));
      }
    }
  }
}
