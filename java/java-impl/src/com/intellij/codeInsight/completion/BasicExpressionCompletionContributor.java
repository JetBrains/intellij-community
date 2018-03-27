// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.KeywordLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
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

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor {

  private static void addKeyword(final Consumer<LookupElement> result, final PsiElement element, final String s) {
    result.consume(createKeywordLookupItem(element, s));
  }

  public static LookupElement createKeywordLookupItem(final PsiElement element, final String s) {
    return new KeywordLookupItem(JavaPsiFacade.getElementFactory(element.getProject()).createKeyword(s, element), element);
  }

  public static void fillCompletionVariants(JavaSmartCompletionParameters parameters,
                                            final Consumer<LookupElement> result,
                                            PrefixMatcher matcher) {
    final PsiElement element = parameters.getPosition();
    if (JavaKeywordCompletion.isAfterTypeDot(element)) {
      addKeyword(result, element, PsiKeyword.CLASS);
      addKeyword(result, element, PsiKeyword.THIS);

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

        for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
          if (!template.isDeactivated() && template.getTemplateContext().isEnabled(new SmartCompletionContextType())) {
            result.consume(new SmartCompletionTemplateItem(template, position));
          }
        }

        addKeyword(result, position, PsiKeyword.TRUE);
        addKeyword(result, position, PsiKeyword.FALSE);

        final PsiElement parent = position.getParent();
        if (parent != null && !(parent.getParent() instanceof PsiSwitchLabelStatement)) {
          for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
            result.consume(new ExpressionLookupItem(expression));
          }
        }

        processDataflowExpressionTypes(position, expectedType, matcher, result);
    }

  }

  public static void processDataflowExpressionTypes(PsiElement position, @Nullable PsiType expectedType, final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    final PsiExpression context = PsiTreeUtil.getParentOfType(position, PsiExpression.class);
    if (context == null) return;

    MultiMap<PsiExpression,PsiType> map = GuessManager.getInstance(position.getProject()).getControlFlowExpressionTypes(context);
    if (map.isEmpty()) {
      return;
    }

    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiLocalVariable) {
          if (!matcher.prefixMatches(((PsiLocalVariable)element).getName())) {
            return true;
          }

          final PsiExpression expression = ((PsiLocalVariable)element).getInitializer();
          if (expression instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand != null) {
              if (map.get(operand).contains(typeCastExpression.getType())) {
                map.remove(operand);
              }
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

  @NotNull
  private static LookupElement expressionToLookupElement(@NotNull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)expression;
      if (!refExpr.isQualified()) {
        final PsiElement target = refExpr.resolve();
        if (target instanceof PsiVariable) {
          final VariableLookupItem item = new VariableLookupItem((PsiVariable)target);
          item.setSubstitutor(PsiSubstitutor.EMPTY);
          return item;
        }
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!call.getMethodExpression().isQualified()) {
        final PsiMethod method = call.resolveMethod();
        if (method != null) {
          return new JavaMethodCallElement(method);
        }
      }
    }

    return new ExpressionLookupItem(expression);
  }

}
