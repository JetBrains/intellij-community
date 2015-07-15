/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
abstract class FluentIterableMethodTransformer {
  private final static Logger LOG = Logger.getInstance(FluentIterableMethodTransformer.class);

  protected abstract String formatMethod(PsiExpression[] initialMethodParameters);

  protected boolean negate() {
    return false;
  }

  @Nullable
  public final PsiMethodCallExpression transform(PsiMethodCallExpression expression, PsiElementFactory elementFactory) {
    final String formatted = formatMethod(expression.getArgumentList().getExpressions());
    final String negation = negate() ? "!" : "";
    final PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
    LOG.assertTrue(qualifierExpression != null);
    final String oldQualifierText = qualifierExpression.getText();
    final String expressionText = negation + oldQualifierText + "." + formatted;
    final PsiElement replaced = expression.replace(elementFactory.createExpressionFromText(expressionText, null));
    if (replaced instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)replaced;
      while (true) {
        final PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
        LOG.assertTrue(qualifier != null);
        if (oldQualifierText.equals(qualifier.getText())) {
          return methodCallExpression;
        }
        methodCallExpression = (PsiMethodCallExpression)qualifier;
      }
    }
    else {
      return null;
    }
  }

  static class OneParameterMethodTransformer extends FluentIterableMethodTransformer {
    private final String myTemplate;
    private final boolean myParameterIsFunctionOrPredicate;

    OneParameterMethodTransformer(String template, boolean parameterIsFunctionOrPredicate) {
      myTemplate = template;
      myParameterIsFunctionOrPredicate = parameterIsFunctionOrPredicate;
    }

    OneParameterMethodTransformer(String parameterIsFunctionOrPredicate) {
      this(parameterIsFunctionOrPredicate, false);
    }

    protected String formatMethod(PsiExpression[] initialMethodParameters) {
      final String matchedElementText;
      if (myParameterIsFunctionOrPredicate) {
        if (initialMethodParameters.length == 1) {
          PsiExpression parameter = initialMethodParameters[0];
          final PsiType type = parameter.getType();
          Boolean role;
          if (!(type instanceof PsiMethodReferenceType) && !(type instanceof PsiLambdaExpressionType)
              && (role = GuavaFunctionAndPredicateConverter.isClassConditionPredicate(parameter)) != null) {
            matchedElementText = GuavaFunctionAndPredicateConverter.convertFunctionOrPredicateParameter(parameter, role);
          } else {
            matchedElementText = parameter.getText();
          }
        } else {
          matchedElementText = "";
        }
      } else {
        matchedElementText = initialMethodParameters.length > 0 ? initialMethodParameters[0].getText() : "";
      }
      return String.format(myTemplate, matchedElementText);
    }
  }

  static class ToArrayMethodTransformer extends FluentIterableMethodTransformer {
    @Override
    protected String formatMethod(PsiExpression[] initialMethodParameters) {
      final PsiExpression parameter = initialMethodParameters[0];
      if (parameter instanceof PsiClassObjectAccessExpression) {
        final PsiType type = ((PsiClassObjectAccessExpression)parameter).getOperand().getType();
        if (type instanceof PsiClassType) {
          final PsiClass resolvedClass = ((PsiClassType)type).resolve();
          if (resolvedClass != null) {
            final String qName = resolvedClass.getQualifiedName();
            if (qName != null) {
              return String.format("toArray(%s[]::new)", qName);
            }
          }
        }
      }
      return "toArray()";
    }
  }

  static class ParameterlessMethodTransformer extends FluentIterableMethodTransformer {
    private final String myTemplate;
    private final boolean myNegation;

    ParameterlessMethodTransformer(String template) {
      this(template.endsWith(")") ? template : (template + "()"), false);
    }

    ParameterlessMethodTransformer(String template, boolean negation) {
      myTemplate = template;
      myNegation = negation;
    }

    @Override
    protected boolean negate() {
      return myNegation;
    }

    @Override
    protected String formatMethod(PsiExpression[] initialMethodParameters) {
      return myTemplate;
    }
  }
}
