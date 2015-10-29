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
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Dmitry Batkovich
 */
public class LongAdderConversionRule extends TypeConversionRule {
  public static final String JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER = "java.util.concurrent.atomic.LongAdder";

  private static final Set<String> IDENTICAL_METHODS
    = ContainerUtil.newHashSet("longValue", "intValue", "doubleValue", "floatValue", "toString");

  private static final Set<String> INCREMENT_DECREMENT_METHODS =
    ContainerUtil.newHashSet("incrementAndGet", "getAndIncrement", "decrementAndGet", "getAndDecrement");

  @Nullable
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from,
                                                     PsiType to,
                                                     PsiMember member,
                                                     PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    if (!(from instanceof PsiClassType) || !(from.getCanonicalText().equals(AtomicInteger.class.getName()) ||
                                             from.getCanonicalText().equals(AtomicLong.class.getName()))) {
      return null;
    }
    if (!(to instanceof PsiClassType) || !(to.getCanonicalText().equals(JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER))) {
      return null;
    }
    if (member != null) {
      final String name = member.getName();
      if (INCREMENT_DECREMENT_METHODS.contains(name)) {
        if (isMethodCallWithIgnoredReturnValue(context)) return null;
        assert name != null;
        String replacementMethodName = name.toLowerCase().contains("increment") ? "increment" : "decrement";
        return new TypeConversionDescriptor("$v$.$method$()", "$v$." + replacementMethodName + "()");
      }
      else if ("getAndAdd".equals(name) || "addAndGet".equals(name)) {
        if (isMethodCallWithIgnoredReturnValue(context)) return null;
        return new TypeConversionDescriptor("$v$.$method$($toAdd$)", "$v$.add($toAdd$)");
      }
      else if ("set".equals(name) || "lazySet".equals(name) || "getAndSet".equals(name)) {
        if ("getAndSet".equals(name) && isMethodCallWithIgnoredReturnValue(context)) return null;
        String template = getParametersCount((PsiCallExpression)context.getParent()) == 1 ? "$l$.reset()" : "$l$.add($v$ - $l$.sum())";
        return new TypeConversionDescriptor("$l$.$setMethodName$($v$)", template);
      }
      else if ("get".equals(name)) {
        return new TypeConversionDescriptor("$l$.get()", "$l$.sum()");
      }
      else if (IDENTICAL_METHODS.contains(name)) {
        return new TypeConversionDescriptorBase();
      }
    }
    else if (context instanceof PsiNewExpression) {
      final int parametersCount = getParametersCount((PsiCallExpression)context);
      if (parametersCount != -1) {
        return new TypeConversionDescriptor("new $className$(" + (parametersCount == 1 ? "$p$" : "") + ")",
                                            "new " + JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER + "()");
      }
    }
    return null;
  }

  private boolean isMethodCallWithIgnoredReturnValue(PsiExpression context) {
    final PsiElement methodCall = context.getParent();
    if (!(methodCall instanceof PsiMethodCallExpression)) {
      return true;
    }
    final PsiElement expressionStatement = methodCall.getParent();
    return !(expressionStatement instanceof PsiExpressionStatement);
  }

  public static byte getParametersCount(@NotNull PsiCallExpression context) {
    final PsiExpressionList list = context.getArgumentList();
    if (list == null) {
      return -1;
    }
    final PsiExpression[] arguments = list.getExpressions();
    if (arguments.length == 1) {
      final PsiExpression argument = arguments[0];
      if (argument instanceof PsiLiteralExpression) {
        final Object value = ((PsiLiteralExpression)argument).getValue();
        if (value instanceof Long && value.equals(0L)) {
          return 1;
        }
        if (value instanceof Integer && value.equals(0)) {
          return 1;
        }
      }
      else {
        return -1;
      }
    }
    else if (arguments.length == 0) {
      return 0;
    }
    return -1;
  }
}
