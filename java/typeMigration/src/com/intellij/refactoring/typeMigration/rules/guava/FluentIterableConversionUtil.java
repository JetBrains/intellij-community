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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.codeInspection.java18StreamApi.StreamApiConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.util.SmartList;
import com.siyeh.ipp.types.ReplaceMethodRefWithLambdaIntention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class FluentIterableConversionUtil {
  private final static Logger LOG = Logger.getInstance(FluentIterableConversionUtil.class);

  @Nullable
  static TypeConversionDescriptor getFilterDescriptor(PsiMethod method) {
    LOG.assertTrue("filter".equals(method.getName()));

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return null;
    final PsiParameter parameter = parameters[0];
    final PsiType type = parameter.getType();
    if (!(type instanceof PsiClassType)) return null;
    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return null;
    if (CommonClassNames.JAVA_LANG_CLASS.equals(resolvedClass.getQualifiedName())) {
      return new GuavaFilterInstanceOfConversionDescriptor();
    }
    else if (GuavaPredicateConversionRule.GUAVA_PREDICATE.equals(resolvedClass.getQualifiedName())) {
      return new LambdaParametersTypeConversionDescriptor("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)");
    }
    return null;
  }

  static class TransformAndConcatConversionRule extends LambdaParametersTypeConversionDescriptor {
    public TransformAndConcatConversionRule() {
      super("$q$.transformAndConcat($params$)", "$q$.flatMap($params$)");
    }

    @Override
    public PsiExpression replace(PsiExpression expression) {
      PsiExpression argument = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0];

      PsiAnonymousClass anonymousClass;
      if (argument instanceof PsiNewExpression &&
          (anonymousClass = ((PsiNewExpression)argument).getAnonymousClass()) != null) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, true)) {
          argument = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(argument, true, true);
        };
      }
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(expression.getProject());
      if (argument != null && !(argument instanceof PsiFunctionalExpression)) {
        argument =
          (PsiExpression)argument.replace(javaPsiFacade.getElementFactory().createExpressionFromText("(" + argument.getText() + ")::apply", null));
      }

      if (argument instanceof PsiMethodReferenceExpression) {
        argument = ReplaceMethodRefWithLambdaIntention.convertMethodReferenceToLambda((PsiMethodReferenceExpression)argument);
      }
      if (argument instanceof PsiLambdaExpression) {
        List<Pair<PsiExpression, Boolean>> iterableReturnValues = new SmartList<Pair<PsiExpression, Boolean>>();

        final PsiElement body = ((PsiLambdaExpression)argument).getBody();
        final PsiClass collection = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, expression.getResolveScope());
        if (collection == null) return expression;
        final PsiClass iterable = javaPsiFacade.findClass(CommonClassNames.JAVA_LANG_ITERABLE, expression.getResolveScope());
        if (iterable == null) return expression;

        if (body instanceof PsiCodeBlock) {
          for (PsiReturnStatement statement : PsiTreeUtil
            .findChildrenOfType(body, PsiReturnStatement.class)) {
            final PsiExpression retValue = statement.getReturnValue();
            if (!determineType(retValue, iterableReturnValues, iterable, collection)) {
              return expression;
            }
          }
        } else if (!(body instanceof PsiExpression) || !determineType((PsiExpression)body, iterableReturnValues, iterable, collection)) {
          return expression;
        }

        for (Pair<PsiExpression, Boolean> returnValueAndIsCollection : iterableReturnValues) {
          convertToStream(returnValueAndIsCollection.getFirst(), returnValueAndIsCollection.getSecond());
        }

      } else {
        return expression;
      }

      return super.replace(expression);
    }

    private static boolean determineType(PsiExpression retValue,
                                         List<Pair<PsiExpression, Boolean>> iterableReturnValues,
                                         PsiClass iterable,
                                         PsiClass collection) {
      if (retValue == null) return false;
      final PsiType type = retValue.getType();
      if (PsiType.NULL.equals(type)) {
        return true;
      }
      if (type instanceof PsiClassType) {
        final PsiClass resolvedClass = ((PsiClassType)type).resolve();

        if (InheritanceUtil.isInheritorOrSelf(resolvedClass, iterable, true)) {
          final boolean isCollection = InheritanceUtil.isInheritorOrSelf(resolvedClass, collection, true);
          iterableReturnValues.add(Pair.create(retValue, isCollection));
          return true;
        }
      }
      return false;
    }

    private static void convertToStream(@NotNull PsiExpression returnValue, boolean isCollection) {
      String expressionAsText = isCollection
                         ? "(" + returnValue.getText() + ").stream()"
                         : "java.util.stream.StreamSupport.stream((" + returnValue.getText() + ").spliterator(), false)";
      returnValue.replace(JavaPsiFacade.getElementFactory(returnValue.getProject()).createExpressionFromText(expressionAsText, returnValue));
    }
  }

  private static class GuavaFilterInstanceOfConversionDescriptor extends TypeConversionDescriptor {
    public GuavaFilterInstanceOfConversionDescriptor() {
      super("$it$.filter($p$)", "$it$." + StreamApiConstants.FILTER + "($p$)");
    }

    @Override
    public PsiExpression replace(PsiExpression expression) {
      final PsiExpression argument = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions()[0];
      final PsiExpression newArgument =
        JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText("(" + argument.getText() + ")::isInstance", argument);
      argument.replace(newArgument);
      return super.replace(expression);
    }
  }


}
