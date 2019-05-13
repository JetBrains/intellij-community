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

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
class JavaLangReflectHandleInvocationChecker {
  private static final Logger LOG = Logger.getInstance(JavaLangReflectHandleInvocationChecker.class);

  private static final String INVOKE = "invoke";
  private static final String INVOKE_EXACT = "invokeExact";
  private static final String INVOKE_WITH_ARGUMENTS = "invokeWithArguments";
  private static final String JAVA_LANG_INVOKE_METHOD_HANDLE = "java.lang.invoke.MethodHandle";

  private static final Set<String> METHOD_HANDLE_INVOKE_NAMES = ContainerUtil.set(INVOKE, INVOKE_EXACT, INVOKE_WITH_ARGUMENTS);

  static boolean checkMethodHandleInvocation(@NotNull PsiMethodCallExpression methodCall, @NotNull ProblemsHolder holder) {
    final String referenceName = methodCall.getMethodExpression().getReferenceName();
    if (METHOD_HANDLE_INVOKE_NAMES.contains(referenceName)) {
      final PsiMethod method = methodCall.resolveMethod();
      if (method != null && isClassWithName(method.getContainingClass(), JAVA_LANG_INVOKE_METHOD_HANDLE)) {
        if (isWithDynamicArguments(methodCall)) {
          return true;
        }
        final PsiExpression qualifierDefinition = findDefinition(methodCall.getMethodExpression().getQualifierExpression());
        if (qualifierDefinition instanceof PsiMethodCallExpression) {
          checkMethodHandleInvocation((PsiMethodCallExpression)qualifierDefinition, methodCall, holder);
        }
      }
      return true;
    }
    return false;
  }

  private static void checkMethodHandleInvocation(@NotNull PsiMethodCallExpression handleFactoryCall,
                                                  @NotNull PsiMethodCallExpression invokeCall,
                                                  @NotNull ProblemsHolder holder) {
    final String factoryMethodName = handleFactoryCall.getMethodExpression().getReferenceName();
    if (factoryMethodName != null && JavaLangInvokeHandleSignatureInspection.KNOWN_METHOD_NAMES.contains(factoryMethodName)) {

      final PsiExpression[] handleFactoryArguments = handleFactoryCall.getArgumentList().getExpressions();
      final boolean isFindConstructor = FIND_CONSTRUCTOR.equals(factoryMethodName);
      if (handleFactoryArguments.length == 3 && !isFindConstructor ||
          handleFactoryArguments.length == 2 && isFindConstructor ||
          handleFactoryArguments.length == 4 && FIND_SPECIAL.equals(factoryMethodName)) {

        final PsiMethod factoryMethod = handleFactoryCall.resolveMethod();
        if (factoryMethod != null && isClassWithName(factoryMethod.getContainingClass(), JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP)) {
          final ReflectiveType receiverType = getReflectiveType(handleFactoryArguments[0]);
          final boolean isExact = INVOKE_EXACT.equals(invokeCall.getMethodExpression().getReferenceName());

          if (isFindConstructor) {
            if (!checkMethodSignature(invokeCall, handleFactoryArguments[1], isExact, true, 0, holder)) return;
            checkReturnType(invokeCall, receiverType, isExact, holder);
            return;
          }

          final PsiExpression typeExpression = handleFactoryArguments[2];
          switch (factoryMethodName) {
            case FIND_VIRTUAL:
            case FIND_SPECIAL:
              if (!checkMethodSignature(invokeCall, typeExpression, isExact, false, 1, holder)) return;
              checkCallReceiver(invokeCall, receiverType, holder);
              break;

            case FIND_STATIC:
              checkMethodSignature(invokeCall, typeExpression, isExact, false, 0, holder);
              break;

            case FIND_GETTER:
              if (!checkGetter(invokeCall, typeExpression, isExact, 1, holder)) return;
              checkCallReceiver(invokeCall, receiverType, holder);
              break;

            case FIND_SETTER:
              if (!checkSetter(invokeCall, typeExpression, isExact, 1, holder)) return;
              checkCallReceiver(invokeCall, receiverType, holder);
              break;

            case FIND_STATIC_GETTER:
              checkGetter(invokeCall, typeExpression, isExact, 0, holder);
              break;

            case FIND_STATIC_SETTER:
              checkSetter(invokeCall, typeExpression, isExact, 0, holder);
              break;

            case FIND_VAR_HANDLE:
              break;

            case FIND_STATIC_VAR_HANDLE:
              break;
          }
        }
      }
    }
  }

  static void checkCallReceiver(@NotNull PsiMethodCallExpression invokeCall,
                                @Nullable ReflectiveType expectedType,
                                ProblemsHolder holder) {
    final PsiExpressionList argumentList = invokeCall.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) return;

    final PsiExpression receiverArgument = arguments[0];
    LOG.assertTrue(receiverArgument != null);
    final PsiExpression receiverDefinition = findDefinition(receiverArgument);
    if (ExpressionUtils.isNullLiteral(receiverDefinition)) {
      holder.registerProblem(receiverArgument, InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.null"));
      return;
    }

    if (expectedType != null) {
      if (!isCompatible(expectedType.getType(), receiverArgument.getType())) {
        holder.registerProblem(receiverArgument,
                               InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.incompatible",
                                                         expectedType.getQualifiedName()));
      }
      else if (receiverArgument != receiverDefinition && receiverDefinition != null) {
        if (!isCompatible(expectedType.getType(), receiverDefinition.getType())) {
          holder.registerProblem(receiverArgument, InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.incompatible",
                                                                             expectedType.getQualifiedName()));
        }
      }
    }
  }

  private static boolean checkMethodSignature(@NotNull PsiMethodCallExpression invokeCall,
                                              @NotNull PsiExpression signatureExpression,
                                              boolean isExact,
                                              boolean isConstructor,
                                              int argumentOffset,
                                              @NotNull ProblemsHolder holder) {
    final List<Supplier<ReflectiveType>> lazyMethodSignature = getLazyMethodSignature(signatureExpression);
    if (lazyMethodSignature == null) return true;

    if (!isConstructor && lazyMethodSignature.size() != 0) {
      final ReflectiveType returnType = lazyMethodSignature.get(0).get();
      checkReturnType(invokeCall, returnType, isExact, holder);
    }

    final PsiExpressionList argumentList = invokeCall.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final JavaReflectionInvocationInspection.Arguments actualArguments =
      JavaReflectionInvocationInspection.getActualMethodArguments(arguments, argumentOffset, false);
    if (actualArguments == null) return true;

    final int requiredArgumentCount = lazyMethodSignature.size() - 1; // -1 excludes the return type
    if (!checkArgumentCount(actualArguments.expressions, requiredArgumentCount, argumentOffset, argumentList, holder)) return false;

    LOG.assertTrue(actualArguments.expressions.length == requiredArgumentCount);
    for (int i = 0; i < requiredArgumentCount; i++) {
      final ReflectiveType requiredType = lazyMethodSignature.get(i + 1).get();
      checkArgumentType(actualArguments.expressions[i], requiredType, argumentList, isExact, holder);
    }
    return true;
  }


  static void checkArgumentType(@NotNull PsiExpression argument,
                                @Nullable ReflectiveType requiredType,
                                @NotNull PsiExpressionList argumentList,
                                boolean isExact,
                                @NotNull ProblemsHolder holder) {
    if (requiredType != null) {
      final PsiType actualType = argument.getType();
      if (actualType != null) {
        if (!isCompatible(requiredType, actualType, isExact)) {
          if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
            holder.registerProblem(argument,
                                   InspectionsBundle.message(isExact
                                                             ? "inspection.reflect.handle.invocation.argument.not.exact"
                                                             : "inspection.reflection.invocation.argument.not.assignable",
                                                             requiredType.getQualifiedName()));
          }
        }
        else if (requiredType.isPrimitive()) {
          final PsiExpression definition = findDefinition(argument);
          if (definition != null && PsiType.NULL.equals(definition.getType())) {
            if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
              holder.registerProblem(argument,
                                     InspectionsBundle.message("inspection.reflect.handle.invocation.primitive.argument.null",
                                                               requiredType.getQualifiedName()));
            }
          }
        }
      }
    }
  }

  static void checkReturnType(@NotNull PsiMethodCallExpression invokeCall,
                              @Nullable ReflectiveType requiredType,
                              boolean isExact,
                              @NotNull ProblemsHolder holder) {
    if (requiredType == null) return;
    final PsiElement invokeParent = invokeCall.getParent();
    PsiType actualType = null;
    PsiElement problemElement = null;
    if (invokeParent instanceof PsiTypeCastExpression) {
      final PsiTypeElement castTypeElement = ((PsiTypeCastExpression)invokeParent).getCastType();
      if (castTypeElement != null) {
        actualType = castTypeElement.getType();
        problemElement = castTypeElement;
      }
    }
    else if (invokeParent instanceof PsiAssignmentExpression) {
      actualType = ((PsiAssignmentExpression)invokeParent).getLExpression().getType();
    }
    else if (invokeParent instanceof PsiVariable) {
      actualType = ((PsiVariable)invokeParent).getType();
    }

    if (actualType != null && !isCompatible(requiredType, actualType, isExact)) {
      if (problemElement == null) {
        problemElement = invokeCall.getMethodExpression();
      }
      holder.registerProblem(problemElement, InspectionsBundle.message(isExact || requiredType.isPrimitive()
                                                                       ? "inspection.reflect.handle.invocation.result.not.exact"
                                                                       : "inspection.reflect.handle.invocation.result.not.assignable",
                                                                       requiredType.getQualifiedName()));
    }
  }

  @Nullable
  private static List<Supplier<ReflectiveType>> getLazyMethodSignature(@Nullable PsiExpression methodTypeExpression) {
    final PsiExpression typeDefinition = findDefinition(methodTypeExpression);
    if (typeDefinition instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression typeDefinitionCall = (PsiMethodCallExpression)typeDefinition;

      if (isCallToMethod(typeDefinitionCall, JAVA_LANG_INVOKE_METHOD_TYPE, METHOD_TYPE)) {
        final PsiExpression[] arguments = typeDefinitionCall.getArgumentList().getExpressions();
        if (arguments.length != 0) {
          return ContainerUtil.map(arguments, argument -> (() -> getReflectiveType(argument)));
        }
      }
      else if (isCallToMethod(typeDefinitionCall, JAVA_LANG_INVOKE_METHOD_TYPE, GENERIC_METHOD_TYPE)) {
        final PsiExpression[] arguments = typeDefinitionCall.getArgumentList().getExpressions();
        final Pair.NonNull<Integer, Boolean> signature = getGenericSignature(arguments);
        if (signature != null) {
          final int objectArgCount = signature.getFirst();
          final boolean finalArray = signature.getSecond();
          if (objectArgCount == 0 && !finalArray) {
            return Collections.emptyList();
          }
          final PsiClassType javaLangObject =
            PsiType.getJavaLangObject(methodTypeExpression.getManager(), methodTypeExpression.getResolveScope());
          final ReflectiveType objectType = ReflectiveType.create(javaLangObject, false);
          final List<ReflectiveType> argumentTypes = new ArrayList<>();
          argumentTypes.add(objectType); // return type
          for (int i = 0; i < objectArgCount; i++) {
            argumentTypes.add(objectType);
          }
          if (finalArray) {
            argumentTypes.add(ReflectiveType.arrayOf(objectType));
          }
          return ContainerUtil.map(argumentTypes, type -> (() -> type));
        }
      }
    }
    return null;
  }

  private static boolean checkGetter(@NotNull PsiMethodCallExpression invokeCall,
                                     @NotNull PsiExpression typeExpression,
                                     boolean isExact,
                                     int argumentOffset, ProblemsHolder holder) {
    final PsiExpressionList argumentList = invokeCall.getArgumentList();
    if (!checkArgumentCount(argumentList.getExpressions(), argumentOffset, 0, argumentList, holder)) return false;

    final ReflectiveType resultType = getReflectiveType(typeExpression);
    if (resultType != null) {
      checkReturnType(invokeCall, resultType, isExact, holder);
    }
    return true;
  }

  private static boolean checkSetter(@NotNull PsiMethodCallExpression invokeCall,
                                     @NotNull PsiExpression typeExpression,
                                     boolean isExact,
                                     int argumentOffset, ProblemsHolder holder) {
    final PsiExpressionList argumentList = invokeCall.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (!checkArgumentCount(arguments, argumentOffset + 1, 0, argumentList, holder)) return false;

    LOG.assertTrue(arguments.length == argumentOffset + 1);
    final ReflectiveType requiredType = getReflectiveType(typeExpression);
    checkArgumentType(arguments[argumentOffset], requiredType, argumentList, isExact, holder);

    final PsiElement invokeParent = invokeCall.getParent();
    if (!(invokeParent instanceof PsiStatement)) {
      holder.registerProblem(invokeCall.getMethodExpression(),
                             InspectionsBundle.message(isExact
                                                       ? "inspection.reflect.handle.invocation.result.void"
                                                       : "inspection.reflect.handle.invocation.result.null"));
    }
    return true;
  }

  static boolean checkArgumentCount(@NotNull PsiExpression[] arguments,
                                    int requiredArgumentCount,
                                    int argumentOffset,
                                    @NotNull PsiElement problemElement,
                                    @NotNull ProblemsHolder holder) {
    if (requiredArgumentCount < 0) return false;
    if (arguments.length != requiredArgumentCount) {
      holder.registerProblem(problemElement, InspectionsBundle.message(
        "inspection.reflection.invocation.argument.count", requiredArgumentCount + argumentOffset));
      return false;
    }
    return true;
  }

  private static boolean isCompatible(@NotNull ReflectiveType requiredType, @NotNull PsiType actualType, boolean isExact) {
    if (isExact) {
      return requiredType.isEqualTo(actualType);
    }
    return requiredType.isAssignableFrom(actualType) || actualType.isAssignableFrom(requiredType.getType());
  }


  private static boolean isCompatible(@NotNull PsiType expectedType, @Nullable PsiType actualType) {
    return actualType != null && (expectedType.isAssignableFrom(actualType) || actualType.isAssignableFrom(expectedType));
  }

  private static boolean isWithDynamicArguments(@NotNull PsiMethodCallExpression invokeCall) {
    if (INVOKE_WITH_ARGUMENTS.equals(invokeCall.getMethodExpression().getReferenceName())) {
      final PsiExpression[] arguments = invokeCall.getArgumentList().getExpressions();
      if (arguments.length == 1) {
        return isVarargAsArray(arguments[0]) ||
               InheritanceUtil.isInheritor(arguments[0].getType(), JAVA_UTIL_LIST);
      }
    }
    return false;
  }
}
