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

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.codeInspection.reflectiveAccess.JavaLangReflectHandleInvocationChecker.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
class JavaLangReflectVarHandleInvocationChecker {
  private static final Logger LOG = Logger.getInstance(JavaLangReflectVarHandleInvocationChecker.class);

  static final String ARRAY_ELEMENT_VAR_HANDLE = "arrayElementVarHandle";
  static final String JAVA_LANG_INVOKE_VAR_HANDLE = "java.lang.invoke.VarHandle";
  static final String JAVA_LANG_INVOKE_METHOD_HANDLES = "java.lang.invoke.MethodHandles";

  private static final String GET = "get";
  private static final String GET_VOLATILE = "getVolatile";
  private static final String GET_OPAQUE = "getOpaque";
  private static final String GET_ACQUIRE = "getAcquire";

  private static final String SET = "set";
  private static final String SET_VOLATILE = "setVolatile";
  private static final String SET_OPAQUE = "setOpaque";
  private static final String SET_RELEASE = "setRelease";

  private static final String GET_AND_SET = "getAndSet";
  private static final String GET_AND_SET_ACQUIRE = "getAndSetAcquire";
  private static final String GET_AND_SET_RELEASE = "getAndSetRelease";

  private static final String GET_AND_ADD = "getAndAdd";
  private static final String GET_AND_ADD_ACQUIRE = "getAndAddAcquire";
  private static final String GET_AND_ADD_RELEASE = "getAndAddRelease";

  private static final String GET_AND_BITWISE_OR = "getAndBitwiseOr";
  private static final String GET_AND_BITWISE_OR_ACQUIRE = "getAndBitwiseOrAcquire";
  private static final String GET_AND_BITWISE_OR_RELEASE = "getAndBitwiseOrRelease";

  private static final String GET_AND_BITWISE_AND = "getAndBitwiseAnd";
  private static final String GET_AND_BITWISE_AND_ACQUIRE = "getAndBitwiseAndAcquire";
  private static final String GET_AND_BITWISE_AND_RELEASE = "getAndBitwiseAndRelease";

  private static final String GET_AND_BITWISE_XOR = "getAndBitwiseXor";
  private static final String GET_AND_BITWISE_XOR_ACQUIRE = "getAndBitwiseXorAcquire";
  private static final String GET_AND_BITWISE_XOR_RELEASE = "getAndBitwiseXorRelease";

  private static final String COMPARE_AND_SET = "compareAndSet";
  private static final String COMPARE_AND_EXCHANGE = "compareAndExchange";
  private static final String COMPARE_AND_EXCHANGE_ACQUIRE = "compareAndExchangeAcquire";
  private static final String COMPARE_AND_EXCHANGE_RELEASE = "compareAndExchangeRelease";

  private static final String WEAK_COMPARE_AND_SET = "weakCompareAndSet";
  private static final String WEAK_COMPARE_AND_SET_ACQUIRE = "weakCompareAndSetAcquire";
  private static final String WEAK_COMPARE_AND_SET_PLAIN = "weakCompareAndSetPlain";
  private static final String WEAK_COMPARE_AND_SET_RELEASE = "weakCompareAndSetRelease";

  private static final ObjectIntHashMap<String> VAR_HANDLE_ARGUMENT_COUNTS = new ObjectIntHashMap<>();

  static {
    for (String name : Arrays.asList(GET, GET_VOLATILE, GET_OPAQUE, GET_ACQUIRE)) {
      VAR_HANDLE_ARGUMENT_COUNTS.put(name, 0);
    }
    for (String name : Arrays.asList(SET, SET_VOLATILE, SET_OPAQUE, SET_RELEASE,
                                     GET_AND_SET, GET_AND_SET_ACQUIRE, GET_AND_SET_RELEASE,
                                     GET_AND_ADD, GET_AND_ADD_ACQUIRE, GET_AND_ADD_RELEASE,
                                     GET_AND_BITWISE_OR, GET_AND_BITWISE_OR_ACQUIRE, GET_AND_BITWISE_OR_RELEASE,
                                     GET_AND_BITWISE_AND, GET_AND_BITWISE_AND_ACQUIRE, GET_AND_BITWISE_AND_RELEASE,
                                     GET_AND_BITWISE_XOR, GET_AND_BITWISE_XOR_ACQUIRE, GET_AND_BITWISE_XOR_RELEASE)) {
      VAR_HANDLE_ARGUMENT_COUNTS.put(name, 1);
    }
    for (String name : Arrays.asList(COMPARE_AND_SET, COMPARE_AND_EXCHANGE, COMPARE_AND_EXCHANGE_ACQUIRE, COMPARE_AND_EXCHANGE_RELEASE,
                                     WEAK_COMPARE_AND_SET, WEAK_COMPARE_AND_SET_ACQUIRE, WEAK_COMPARE_AND_SET_PLAIN,
                                     WEAK_COMPARE_AND_SET_RELEASE)) {
      VAR_HANDLE_ARGUMENT_COUNTS.put(name, 2);
    }
  }

  private static final Set<String> WITH_RETURN_VALUE_NAMES =
    ContainerUtil.set(GET, GET_VOLATILE, GET_OPAQUE, GET_ACQUIRE,
                      GET_AND_SET, GET_AND_SET_ACQUIRE, GET_AND_SET_RELEASE,
                      GET_AND_ADD, GET_AND_ADD_ACQUIRE, GET_AND_ADD_RELEASE,
                      GET_AND_BITWISE_OR, GET_AND_BITWISE_OR_ACQUIRE, GET_AND_BITWISE_OR_RELEASE,
                      GET_AND_BITWISE_AND, GET_AND_BITWISE_AND_ACQUIRE, GET_AND_BITWISE_AND_RELEASE,
                      GET_AND_BITWISE_XOR, GET_AND_BITWISE_XOR_ACQUIRE, GET_AND_BITWISE_XOR_RELEASE,
                      COMPARE_AND_EXCHANGE, COMPARE_AND_EXCHANGE_ACQUIRE, COMPARE_AND_EXCHANGE_RELEASE);


  static boolean checkVarHandleAccess(PsiMethodCallExpression methodCall, @NotNull ProblemsHolder holder) {
    if (isVarHandleAccessMethod(methodCall)) {
      final PsiExpression qualifierDefinition = findDefinition(methodCall.getMethodExpression().getQualifierExpression());
      if (qualifierDefinition instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression handleFactoryCall = (PsiMethodCallExpression)qualifierDefinition;
        final PsiExpression[] factoryArguments = handleFactoryCall.getArgumentList().getExpressions();

        if (isCallToMethod(handleFactoryCall, JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP, FIND_VAR_HANDLE)) {
          if (factoryArguments.length == 3) {
            checkCallReceiver(methodCall, getReflectiveType(factoryArguments[0]), holder);

            checkVarHandleAccessSignature(methodCall, getReflectiveType(factoryArguments[2]), 1, holder);
          }
        }
        else if (isCallToMethod(handleFactoryCall, JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP, FIND_STATIC_VAR_HANDLE)) {
          if (factoryArguments.length == 3) {
            checkVarHandleAccessSignature(methodCall, getReflectiveType(factoryArguments[2]), 0, holder);
          }
        }
        else if (isCallToMethod(handleFactoryCall, JAVA_LANG_INVOKE_METHOD_HANDLES, ARRAY_ELEMENT_VAR_HANDLE)) {
          if (factoryArguments.length == 1) {
            final ReflectiveType arrayType = getReflectiveType(factoryArguments[0]);
            if (arrayType != null) {
              checkCallReceiver(methodCall, arrayType, holder);

              final ReflectiveType valueType = arrayType.getArrayComponentType();
              if (valueType != null) {
                checkVarHandleAccessSignature(methodCall, valueType, 2, holder);
              }
            }
          }
        }
      }
      return true;
    }
    return false;
  }

  private static void checkVarHandleAccessSignature(@NotNull PsiMethodCallExpression accessCall,
                                                    @Nullable ReflectiveType valueType,
                                                    int coordinateArguments,
                                                    @NotNull ProblemsHolder holder) {
    if (valueType == null) return;

    if (isWithReturnValue(accessCall)) {
      checkReturnType(accessCall, valueType, false, holder);
    }

    final PsiExpressionList accessArgumentList = accessCall.getArgumentList();
    final PsiExpression[] accessArguments = accessArgumentList.getExpressions();

    final int requiredArgumentCount = getVarHandleArgumentCount(accessCall, coordinateArguments);
    if (!checkArgumentCount(accessArguments, requiredArgumentCount, 0, accessArgumentList, holder)) return;

    LOG.assertTrue(accessArguments.length == requiredArgumentCount);
    for (int i = coordinateArguments; i < requiredArgumentCount; i++) {
      checkArgumentType(accessArguments[i], valueType, accessArgumentList, false, holder);
    }
  }

  private static boolean isVarHandleAccessMethod(PsiMethodCallExpression methodCall) {
    final String methodName = methodCall.getMethodExpression().getReferenceName();
    if (VAR_HANDLE_ARGUMENT_COUNTS.containsKey(methodName)) {
      final PsiMethod method = methodCall.resolveMethod();
      return method != null && isClassWithName(method.getContainingClass(), JAVA_LANG_INVOKE_VAR_HANDLE);
    }
    return false;
  }

  private static int getVarHandleArgumentCount(@NotNull PsiMethodCallExpression accessCall, int coordinateArguments) {
    final String name = accessCall.getMethodExpression().getReferenceName();
    final int count = VAR_HANDLE_ARGUMENT_COUNTS.get(name);
    return count >= 0 ? count + coordinateArguments : -1;
  }

  private static boolean isWithReturnValue(@NotNull PsiMethodCallExpression accessCall) {
    final String name = accessCall.getMethodExpression().getReferenceName();
    return WITH_RETURN_VALUE_NAMES.contains(name);
  }
}
