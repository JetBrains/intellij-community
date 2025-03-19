// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.text.StringUtil;
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
public final class LongAdderConversionRule extends TypeConversionRule {
  public static final String JAVA_UTIL_CONCURRENT_ATOMIC_LONG_ADDER = "java.util.concurrent.atomic.LongAdder";

  private static final Set<String> IDENTICAL_METHODS
    = ContainerUtil.newHashSet("longValue", "intValue", "doubleValue", "floatValue", "toString");

  private static final Set<String> INCREMENT_DECREMENT_METHODS =
    ContainerUtil.newHashSet("incrementAndGet", "getAndIncrement", "decrementAndGet", "getAndDecrement");

  @Override
  public @Nullable TypeConversionDescriptorBase findConversion(PsiType from,
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
        String replacementMethodName = StringUtil.toLowerCase(name).contains("increment") ? "increment" : "decrement";
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

  private static boolean isMethodCallWithIgnoredReturnValue(PsiExpression context) {
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
