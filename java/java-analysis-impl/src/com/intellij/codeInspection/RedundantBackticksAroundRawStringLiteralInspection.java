// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RedundantBackticksAroundRawStringLiteralInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (PsiUtil.getLanguageLevel(holder.getFile()) != LanguageLevel.JDK_11_PREVIEW) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        if (((PsiLiteralExpressionImpl)expression).getLiteralElementType() == JavaTokenType.RAW_STRING_LITERAL) {
          String text = expression.getText();
          String rawString = ((PsiLiteralExpressionImpl)expression).getRawString();
          int reducedNumberOfBackTicks = PsiRawStringLiteralUtil.getReducedNumberOfBackticks(text);
          if (reducedNumberOfBackTicks > 0) {
            String newBackticksSequence = StringUtil.repeat("`", reducedNumberOfBackTicks);
            int redundantTicksLength = (text.length() - rawString.length()) / 2 - reducedNumberOfBackTicks;
            holder.registerProblem(expression, "Number of backticks may be reduced by " + redundantTicksLength, 
                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                   new TextRange(0, redundantTicksLength),
                                   new LocalQuickFix() {
              @Nls
              @NotNull
              @Override
              public String getFamilyName() {
                return "Reduce number of backticks";
              }
  
              @Override
              public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                PsiElement element = descriptor.getPsiElement();
                PsiExpression newRawStringLiteral = JavaPsiFacade.getElementFactory(project)
                                                                 .createExpressionFromText(
                                                                   newBackticksSequence + rawString + newBackticksSequence, element);
                element.replace(newRawStringLiteral);
              }
            });
          }
        }
      }
    };
  }
}
