/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionInvocationInspection extends BaseJavaBatchLocalInspectionTool {

  private static final String JAVA_LANG_REFLECT_METHOD = "java.lang.reflect.Method";
  private static final String JAVA_LANG_REFLECT_CONSTRUCTOR = "java.lang.reflect.Constructor";

  private static final String INVOKE = "invoke";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);

        if (isCallToMethod(methodCall, JAVA_LANG_REFLECT_METHOD, INVOKE)) {
          checkReflectionCall(methodCall, 1, holder, JavaReflectionInvocationInspection::isReflectionMethod);
        }
        else if (isCallToMethod(methodCall, JAVA_LANG_REFLECT_CONSTRUCTOR, NEW_INSTANCE)) {
          checkReflectionCall(methodCall, 0, holder, JavaReflectionInvocationInspection::isReflectionConstructor);
        }
      }
    };
  }

  private static boolean isReflectionMethod(PsiMethodCallExpression callExpression) {
    return isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_METHOD) ||
           isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_DECLARED_METHOD);
  }

  private static boolean isReflectionConstructor(PsiMethodCallExpression callExpression) {
    return isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_CONSTRUCTOR) ||
           isCallToMethod(callExpression, JAVA_LANG_CLASS, GET_DECLARED_CONSTRUCTOR);
  }

  private static void checkReflectionCall(@NotNull PsiMethodCallExpression methodCall,
                                          int argumentOffset,
                                          @NotNull ProblemsHolder holder,
                                          @NotNull Predicate<PsiMethodCallExpression> methodPredicate) {
    final List<PsiExpression> requiredTypes =
      getRequiredMethodArguments(methodCall.getMethodExpression().getQualifierExpression(), argumentOffset, methodPredicate);
    if (requiredTypes != null) {
      final PsiExpressionList argumentList = methodCall.getArgumentList();
      final Arguments actualArguments = getActualMethodArguments(argumentList.getExpressions(), argumentOffset, true);
      if (actualArguments != null) {
        if (requiredTypes.size() != actualArguments.expressions.length) {
          if (actualArguments.varargAsArray) {
            final PsiExpression[] expressions = argumentList.getExpressions();
            final PsiElement element = expressions.length == argumentOffset + 1 ? expressions[argumentOffset] : argumentList;
            holder.registerProblem(element, InspectionsBundle.message(
              "inspection.reflection.invocation.item.count", requiredTypes.size()));
          }
          else {
            holder.registerProblem(argumentList, InspectionsBundle.message(
              "inspection.reflection.invocation.argument.count", requiredTypes.size() + argumentOffset));
          }
          return;
        }

        for (int i = 0; i < requiredTypes.size(); i++) {
          final ReflectiveType requiredType = getReflectiveType(requiredTypes.get(i));
          if (requiredType != null) {
            final PsiExpression argument = actualArguments.expressions[i];
            final PsiType actualType = argument.getType();
            if (actualType != null && !requiredType.isAssignableFrom(actualType)) {
              if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
                // either varargs or in-place arguments array
                holder.registerProblem(argument, InspectionsBundle.message(actualArguments.varargAsArray
                                                                           ? "inspection.reflection.invocation.item.not.assignable"
                                                                           : "inspection.reflection.invocation.argument.not.assignable",
                                                                           requiredType.getQualifiedName()));
              }
              else {
                // arguments array in a variable
                final PsiExpression[] expressions = argumentList.getExpressions();
                final PsiElement element = expressions.length == argumentOffset + 1 ? expressions[argumentOffset] : argumentList;
                holder.registerProblem(element, InspectionsBundle.message(
                  "inspection.reflection.invocation.array.not.assignable", actualArguments.expressions.length));
                break;
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  private static List<PsiExpression> getRequiredMethodArguments(@Nullable PsiExpression qualifier,
                                                                int argumentOffset,
                                                                @NotNull Predicate<PsiMethodCallExpression> methodPredicate) {
    final PsiExpression definition = findDefinition(ParenthesesUtils.stripParentheses(qualifier));
    if (definition instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression definitionCall = (PsiMethodCallExpression)definition;
      if (methodPredicate.test(definitionCall)) {
        return getRequiredMethodArguments(definitionCall, argumentOffset);
      }
    }
    return null;
  }

  private static List<PsiExpression> getRequiredMethodArguments(@NotNull PsiMethodCallExpression definitionCall, int argumentOffset) {
    final PsiExpression[] arguments = definitionCall.getArgumentList().getExpressions();

    if (arguments.length == argumentOffset + 1) {
      final PsiExpression[] arrayElements = getVarargAsArray(arguments[argumentOffset]);
      if (arrayElements != null) {
        return Arrays.asList(arrayElements);
      }
    }
    if (arguments.length >= argumentOffset) {
      return Arrays.asList(arguments).subList(argumentOffset, arguments.length);
    }
    return null;
  }

  @Nullable
  public static List<ReflectiveType> getReflectionMethodParameterTypes(@NotNull PsiMethodCallExpression definitionCall,
                                                                       int argumentOffset) {
    List<PsiExpression> arguments = getRequiredMethodArguments(definitionCall, argumentOffset);
    return arguments != null ? ContainerUtil.map(arguments, type -> getReflectiveType(type)) : null;
  }

  @Nullable
  static Arguments getActualMethodArguments(PsiExpression[] arguments, int argumentOffset, boolean allowVarargAsArray) {
    if (allowVarargAsArray && arguments.length == argumentOffset + 1) {
      final PsiExpression[] expressions = getVarargAsArray(arguments[argumentOffset]);
      if (expressions != null) {
        return new Arguments(expressions, true);
      }
    }
    if (arguments.length >= argumentOffset) {
      final PsiExpression[] expressions = argumentOffset != 0 ? Arrays.copyOfRange(arguments, argumentOffset, arguments.length) : arguments;
      for (int i = 0; i < expressions.length; i++) {
        final PsiExpression castOperand = unwrapDisambiguatingCastToObject(expressions[i]);
        if (castOperand != null) {
          expressions[i] = castOperand;
        }
      }
      return new Arguments(expressions, false);
    }
    return null;
  }

  @Nullable
  private static PsiExpression[] getVarargAsArray(@Nullable PsiExpression maybeArray) {
    if (isVarargAsArray(maybeArray)) {
      final PsiExpression argumentsDefinition = findDefinition(maybeArray);
      if (argumentsDefinition instanceof PsiArrayInitializerExpression) {
        return ((PsiArrayInitializerExpression)argumentsDefinition).getInitializers();
      }
      if (argumentsDefinition instanceof PsiNewExpression) {
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)argumentsDefinition).getArrayInitializer();
        if (arrayInitializer != null) {
          return arrayInitializer.getInitializers();
        }
        final PsiExpression[] dimensions = ((PsiNewExpression)argumentsDefinition).getArrayDimensions();
        if (dimensions.length == 1) { // special case: new Object[0]
          final Integer itemCount = computeConstantExpression(findDefinition(dimensions[0]), Integer.class);
          if (itemCount != null && itemCount == 0) {
            return PsiExpression.EMPTY_ARRAY;
          }
        }
      }
    }
    return null;
  }

  @Contract("null -> false")
  static boolean isVarargAsArray(@Nullable PsiExpression maybeArray) {
    final PsiType type = maybeArray != null ? maybeArray.getType() : null;
    return type instanceof PsiArrayType &&
           type.getArrayDimensions() == 1 &&
           type.getDeepComponentType() instanceof PsiClassType;
  }

  @Nullable
  private static PsiExpression unwrapDisambiguatingCastToObject(@Nullable PsiExpression expression) {
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCast = (PsiTypeCastExpression)expression;
      final PsiTypeElement castElement = typeCast.getCastType();
      if (castElement != null && castElement.getType().equalsToText(JAVA_LANG_OBJECT)) {
        return typeCast.getOperand();
      }
    }
    return null;
  }

  static class Arguments {
    final PsiExpression[] expressions;
    final boolean varargAsArray;

    public Arguments(PsiExpression[] expressions, boolean varargAsArray) {
      this.expressions = expressions;
      this.varargAsArray = varargAsArray;
    }
  }
}
