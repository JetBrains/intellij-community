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
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class GuavaOptionalConverter {
  private static final Logger LOG = Logger.getInstance(GuavaOptionalConverter.class);
  private static final Map<String, FluentIterableMethodTransformer> METHODS_CONVERSION = new HashMap<String, FluentIterableMethodTransformer>();
  private static final String OR_METHOD = "or";

  static {
    METHODS_CONVERSION.put("isPresent", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("isPresent"));
    METHODS_CONVERSION.put("get", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("get"));
    METHODS_CONVERSION.put(OR_METHOD, new FluentIterableMethodTransformer.OneParameterMethodTransformer("orElse(%s)"));
    METHODS_CONVERSION.put("orNull", new FluentIterableMethodTransformer.ParameterlessMethodTransformer("orElse(null)"));
  }

  public static boolean isConvertibleIfOption(PsiMethodCallExpression methodCallExpression) {
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    if (!GuavaFluentIterableInspection.GUAVA_OPTIONAL.equals(aClass.getQualifiedName())) {
      return true;
    }
    return METHODS_CONVERSION.containsKey(method.getName());
  }

  @NotNull
  public static PsiMethodCallExpression convertGuavaOptionalToJava(final PsiMethodCallExpression methodCall,
                                                                   final PsiElementFactory elementFactory) {
    final String methodName = methodCall.getMethodExpression().getReferenceName();
    if (methodName == OR_METHOD) {
      final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      if (arguments.length != 1) {
        return methodCall;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      if (type instanceof PsiClassType) {
        final PsiClass resolvedClass = ((PsiClassType)type).resolve();
        if (resolvedClass != null) {
          final String qName = resolvedClass.getQualifiedName();
          if (GuavaFluentIterableInspection.GUAVA_OPTIONAL.equals(qName) || "com.google.common.base.Supplier".equals(qName)) {
            return methodCall;
          }
        }
      }
    }
    final FluentIterableMethodTransformer conversion = METHODS_CONVERSION.get(methodName);
    if (conversion == null) {
      return methodCall;
    }
    final PsiMethodCallExpression transformed = conversion.transform(methodCall, elementFactory);
    LOG.assertTrue(transformed != null);
    return transformed;
  }
}
