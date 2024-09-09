// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.surroundWith.JavaExpressionModCommandSurrounder;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.JavaBundle;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.Conditions.and;

public class SwitchStatementPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {

  private static final Condition<PsiElement> SWITCH_TYPE = e -> {
    if (!(e instanceof PsiExpression expression)) return false;

    return DumbService.getInstance(expression.getProject()).computeWithAlternativeResolveEnabled(() -> {
      final PsiType type = expression.getType();

      if (type == null) return false;
      if (PsiTypes.intType().isAssignableFrom(type)) return true;
      if (type instanceof PsiClassType classType) {
        if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, expression)) return true;

        final PsiClass psiClass = classType.resolve();
        if (psiClass != null && psiClass.isEnum()) return true;
      }

      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && expression.getContainingFile() instanceof PsiJavaFile javaFile) {
        if (PsiUtil.isAvailable(JavaFeature.STRING_SWITCH, javaFile)) return true;
      }

      if (PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, expression) &&
          TypeConversionUtil.isPrimitiveAndNotNull(type)) return true;

      return false;
    });
  };

  public SwitchStatementPostfixTemplate() {
    super("switch", "switch(expr)", JavaPostfixTemplatesUtils.JAVA_PSI_INFO, selectorTopmost(SWITCH_TYPE));
  }

  @Override
  protected @NotNull Surrounder getSurrounder() {
    return new JavaExpressionModCommandSurrounder() {
      @Override
      public boolean isApplicable(PsiExpression expr) {
        return expr.isPhysical() && SWITCH_TYPE.value(expr);
      }

      @Override
      protected void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater) {
        Project project = context.project();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

        PsiElement parent = expr.getParent();
        updater.select(TextRange.from(context.offset(), 0));
        if (parent instanceof PsiExpressionStatement) {
          PsiSwitchStatement switchStatement = (PsiSwitchStatement)factory.createStatementFromText("switch(1){case 1:}", null);
          postprocessSwitch(updater, expr, codeStyleManager, parent, switchStatement);
        }
        else if (PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, expr)) {
          PsiSwitchExpression switchExpression = (PsiSwitchExpression)factory.createExpressionFromText("switch(1){case 1->1;}", null);
          postprocessSwitch(updater, expr, codeStyleManager, expr, switchExpression);
        }
      }

      private static void postprocessSwitch(@NotNull ModPsiUpdater updater,
                                            PsiExpression expr,
                                            CodeStyleManager codeStyleManager,
                                            PsiElement toReplace,
                                            PsiSwitchBlock switchBlock) {
        Document document = expr.getContainingFile().getFileDocument();
        switchBlock = (PsiSwitchBlock)codeStyleManager.reformat(switchBlock);
        PsiExpression selectorExpression = switchBlock.getExpression();
        if (selectorExpression != null) {
          selectorExpression.replace(expr);
        }

        switchBlock = (PsiSwitchBlock)toReplace.replace(switchBlock);

        PsiCodeBlock body = switchBlock.getBody();
        if (body != null) {
          body = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(body);
          if (body != null) {
            TextRange range = body.getStatements()[0].getTextRange();
            document.deleteString(range.getStartOffset(), range.getEndOffset());
            updater.select(TextRange.from(range.getStartOffset(), 0));
          }
        }
      }

      @Override
      public String getTemplateDescription() {
        return JavaBundle.message("switch.stmt.template.description");
      }
    };
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        boolean isEnhancedSwitchAvailable = PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, context);
        List<PsiElement> result = new ArrayList<>();

        for (PsiElement element = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpression.class, PsiStatement.class);
             element instanceof PsiExpression; element = element.getParent()) {
          PsiElement parent = element.getParent();
          if (parent instanceof PsiExpressionStatement) {
            result.add(element);
          }
          else if (isEnhancedSwitchAvailable && (isVariableInitializer(element, parent) ||
                                                 isRightSideOfAssignment(element, parent) ||
                                                 isReturnValue(element, parent) ||
                                                 isArgumentList(parent))) {
            result.add(element);
          }
        }
        return result;
      }

      @Override
      protected Condition<PsiElement> getFilters(int offset) {
        return and(super.getFilters(offset), getPsiErrorFilter());
      }

      @Override
      public @NotNull Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }

      private static boolean isVariableInitializer(PsiElement element, PsiElement parent) {
        return parent instanceof PsiVariable && ((PsiVariable)parent).getInitializer() == element;
      }

      private static boolean isRightSideOfAssignment(PsiElement element, PsiElement parent) {
        return parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getRExpression() == element;
      }

      private static boolean isReturnValue(PsiElement element, PsiElement parent) {
        return parent instanceof PsiReturnStatement && ((PsiReturnStatement)parent).getReturnValue() == element;
      }

      private static boolean isArgumentList(PsiElement parent) {
        return parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCall;
      }
    };
  }
}
