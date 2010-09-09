/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ClassLiteralGetter;
import com.intellij.psi.filters.getters.MembersGetter;
import com.intellij.psi.filters.getters.ThisGetter;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.not;

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor extends ExpressionSmartCompletionContributor{

  private static void addKeyword(final CompletionResultSet result, final PsiElement element, final String s) {
    result.addElement(createKeywordLookupItem(element, s));
  }

  public static LookupElement createKeywordLookupItem(final PsiElement element, final String s) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupItem>() {
      public LookupItem compute() {
        try {
          final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
          return new KeywordLookupItem(keyword, element).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }


  public BasicExpressionCompletionContributor() {
    extend(PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        addKeyword(result, element, PsiKeyword.CLASS);
        addKeyword(result, element, PsiKeyword.THIS);
      }

    });

    extend(not(psiElement().afterLeaf(".")), new CollectionsUtilityMethodsProvider());
    extend(not(psiElement().afterLeaf(".")), new ClassLiteralGetter());

    extend(not(psiElement().afterLeaf(".")), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final PsiType expectedType = parameters.getExpectedType();

        for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
          if (!template.isDeactivated() && template.getTemplateContext().isEnabled(new SmartCompletionContextType())) {
            result.addElement(new SmartCompletionTemplateItem(template, position));
          }
        }

        addKeyword(result, position, PsiKeyword.TRUE);
        addKeyword(result, position, PsiKeyword.FALSE);

        final PsiElement parent = position.getParent();
        if (parent != null && !(parent.getParent() instanceof PsiSwitchLabelStatement)) {
          MembersGetter.addMembers(parameters.getPosition(), expectedType, result);
          if (!parameters.getDefaultType().equals(expectedType)) {
            MembersGetter.addMembers(parameters.getPosition(), parameters.getDefaultType(), result);
          }

          for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
            result.addElement(new ExpressionLookupItem(expression));
          }
        }

        processDataflowExpressionTypes(position, expectedType, result.getPrefixMatcher(), new Consumer<LookupElement>() {
          public void consume(LookupElement decorator) {
            result.addElement(decorator);
          }
        });
      }
    });

  }

  public static void processDataflowExpressionTypes(PsiElement position, @Nullable PsiType expectedType, final PrefixMatcher matcher, Consumer<LookupElement> consumer) {
    final PsiExpression context = PsiTreeUtil.getParentOfType(position, PsiExpression.class);
    if (context == null) return;

    final Map<PsiExpression,PsiType> map = GuessManager.getInstance(position.getProject()).getControlFlowExpressionTypes(context);
    if (map.isEmpty()) {
      return;
    }

    PsiScopesUtil.treeWalkUp(new BaseScopeProcessor() {
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PsiLocalVariable) {
          if (!matcher.prefixMatches(((PsiLocalVariable)element).getName())) {
            return true;
          }

          final PsiExpression expression = ((PsiLocalVariable)element).getInitializer();
          if (expression instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand != null) {
              final PsiType dfaCasted = map.get(operand);
              if (dfaCasted != null && dfaCasted.equals(typeCastExpression.getType())) {
                map.remove(operand);
              }
            }
          }
        }
        return true;
      }
    }, context, context.getContainingFile());

    for (final PsiExpression expression : map.keySet()) {
      final PsiType castType = map.get(expression);
      final PsiType baseType = expression.getType();
      if (expectedType == null || (expectedType.isAssignableFrom(castType) && (baseType == null || !expectedType.isAssignableFrom(baseType)))) {
        consumer.consume(CastingLookupElementDecorator.createCastingElement(expressionToLookupElement(expression), castType));
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
