// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.KeywordLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ClassLiteralGetter;
import com.intellij.psi.filters.getters.ThisGetter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BasicExpressionCompletionContributor {

  private static void addKeyword(final Consumer<? super LookupElement> result, final PsiElement element, final String s) {
    result.consume(createKeywordLookupItem(element, s));
  }

  public static LookupElement createKeywordLookupItem(final PsiElement element, final String s) {
    return new KeywordLookupItem(JavaPsiFacade.getElementFactory(element.getProject()).createKeyword(s, element), element);
  }

  public static void fillCompletionVariants(JavaSmartCompletionParameters parameters,
                                            final Consumer<? super LookupElement> result,
                                            PrefixMatcher matcher) {
    final PsiElement element = parameters.getPosition();
    if (JavaKeywordCompletion.isAfterTypeDot(element)) {
      addKeyword(result, element, JavaKeywords.CLASS);
      addKeyword(result, element, JavaKeywords.THIS);

    }

    if (!JavaKeywordCompletion.AFTER_DOT.accepts(element)) {
      if (parameters.getParameters().getInvocationCount() <= 1) {
        new CollectionsUtilityMethodsProvider(parameters.getPosition(),
                                              parameters.getExpectedType(),
                                              parameters.getDefaultType(), result)
          .addCompletions(StringUtil.isNotEmpty(matcher.getPrefix()));
      }
      ClassLiteralGetter.addCompletions(parameters, result, matcher);

      final PsiElement position = parameters.getPosition();
      final PsiType expectedType = parameters.getExpectedType();

        SmartCompletionContextType smartCompletionContextType = TemplateContextTypes.getByClass(SmartCompletionContextType.class);
        for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
          if (!template.isDeactivated() && template.getTemplateContext().isEnabled(smartCompletionContextType)) {
            result.consume(new SmartCompletionTemplateItem(template, position));
          }
        }

        addKeyword(result, position, JavaKeywords.TRUE);
        addKeyword(result, position, JavaKeywords.FALSE);

        if (!JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
          for (PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
            result.consume(new ExpressionLookupItem(expression));
          }
        }

        processDataflowExpressionTypes(parameters, expectedType, matcher, result);
    }
  }

  static void processDataflowExpressionTypes(JavaSmartCompletionParameters parameters, @Nullable PsiType expectedType, final PrefixMatcher matcher, Consumer<? super LookupElement> consumer) {
    final PsiExpression context = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    if (context == null) return;

    MultiMap<PsiExpression,PsiType> map = GuessManager.getInstance(context.getProject()).getControlFlowExpressionTypes(context, parameters.getParameters().getInvocationCount() > 1);
    if (map.isEmpty()) {
      return;
    }

    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiLocalVariable var) {
          if (!matcher.prefixMatches(var.getName())) {
            return true;
          }

          final PsiExpression expression = var.getInitializer();
          if (expression instanceof PsiTypeCastExpression typeCastExpression) {
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand != null && map.get(operand).contains(typeCastExpression.getType())) {
              map.remove(operand);
            }
          }
        }
        return true;
      }
    }, context, context.getContainingFile());

    for (PsiExpression expression : map.keySet()) {
      for (PsiType castType : map.get(expression)) {
        PsiType baseType = expression.getType();
        if (expectedType == null || (expectedType.isAssignableFrom(castType) && (baseType == null || !expectedType.isAssignableFrom(baseType)))) {
          consumer.consume(CastingLookupElementDecorator.createCastingElement(expressionToLookupElement(expression), castType));
        }
      }
    }
  }

  private static @NotNull LookupElement expressionToLookupElement(@NotNull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression refExpr && !refExpr.isQualified() && refExpr.resolve() instanceof PsiVariable var) {
      final VariableLookupItem item = new VariableLookupItem(var);
      item.setSubstitutor(PsiSubstitutor.EMPTY);
      return item;
    }
    if (expression instanceof PsiMethodCallExpression call && !call.getMethodExpression().isQualified()) {
      final PsiMethod method = call.resolveMethod();
      if (method != null) {
        return new JavaMethodCallElement(method);
      }
    }

    return new ExpressionLookupItem(expression);
  }

}
