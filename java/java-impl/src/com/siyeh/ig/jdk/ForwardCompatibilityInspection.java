// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.style.UnnecessarySemicolonInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ForwardCompatibilityInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(holder.getFile());
    return new JavaElementVisitor() {
      @Override
      public void visitIdentifier(@NotNull PsiIdentifier identifier) {
        String message = getIdentifierWarning(identifier);
        if (message != null) {
          holder.registerProblem(identifier, message, new RenameFix());
        }
      }

      private @Nullable @InspectionMessage String getIdentifierWarning(PsiIdentifier identifier) {
        String name = identifier.getText();
        PsiElement parent = identifier.getParent();
        JavaFeature feature = PsiUtil.softKeywordFeature(name);
        if (feature != null && 
            feature != JavaFeature.MODULES && !name.equals(JavaKeywords.WHEN) &&// Keywords from module-info and 'when' still can be used as class names
            !feature.isSufficient(languageLevel) && parent instanceof PsiClass) {
          return JavaErrorBundle.message("restricted.identifier.warn", name,
                                         feature.getMinimumLevel().feature());
        }
        switch (name) {
          case JavaKeywords.ASSERT -> {
            if (!JavaFeature.ASSERTIONS.isSufficient(languageLevel) &&
                (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
              return JavaErrorBundle.message("assert.identifier.warn");
            }
          }
          case JavaKeywords.ENUM -> {
            if (!JavaFeature.ENUMS.isSufficient(languageLevel) &&
                (parent instanceof PsiClass || parent instanceof PsiMethod || parent instanceof PsiVariable)) {
              return JavaErrorBundle.message("enum.identifier.warn");
            }
          }
          case "_" -> {
            if (languageLevel.isLessThan(LanguageLevel.JDK_1_9)) {
              return JavaErrorBundle.message("underscore.identifier.warn");
            }
          }
        }
        return null;
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        PsiReferenceExpression ref = expression.getMethodExpression();
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null && JavaKeywords.YIELD.equals(nameElement.getText()) && ref.getQualifierExpression() == null &&
            !JavaFeature.SWITCH_EXPRESSION.isSufficient(languageLevel)) {
          PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(expression.getMethodExpression());
          String message = JavaErrorBundle.message("yield.unqualified.method.warn");
          if (qualifier != null) {
            holder.registerProblem(nameElement, message, new QualifyCallFix(), new RenameFix());
          } else {
            holder.registerProblem(nameElement, message, new RenameFix());
          }
        }
      }

      @Override
      public void visitKeyword(@NotNull PsiKeyword keyword) {
        super.visitKeyword(keyword);
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !languageLevel.isAtLeast(LanguageLevel.JDK_10)) {
          @PsiModifier.ModifierConstant String modifier = keyword.getText();
          if (JavaKeywords.STATIC.equals(modifier) || JavaKeywords.TRANSITIVE.equals(modifier)) {
            PsiElement parent = keyword.getParent();
            if (parent instanceof PsiModifierList) {
              PsiElement grand = parent.getParent();
              if (grand instanceof PsiRequiresStatement && PsiJavaModule.JAVA_BASE.equals(((PsiRequiresStatement)grand).getModuleName())) {
                String message = JavaErrorBundle.message("module.unwanted.modifier.warn");
                LocalQuickFix fix = QuickFixFactory.getInstance().createModifierListFix((PsiModifierList)parent, modifier, false, false);
                holder.registerProblem(keyword, message, fix);
              }
            }
          }
        }
      }

      @Override
      public void visitJavaToken(@NotNull PsiJavaToken token) {
        super.visitJavaToken(token);
        if (languageLevel.isLessThan(LanguageLevel.JDK_21) &&
            token.getTokenType() == JavaTokenType.SEMICOLON &&
            PsiUtil.isFollowedByImport(token)) {
          String message = JavaErrorBundle.message("redundant.semicolon.warn");
          holder.registerProblem(token, message, new UnnecessarySemicolonInspection.UnnecessarySemicolonFix());
        }
      }
    };
  }

  private static class QualifyCallFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("qualify.call.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return;
      call.getMethodExpression().setQualifierExpression(qualifier);
    }
  }
}
