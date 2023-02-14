// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COMPARATOR;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * Simplified model for comparator: does not perform actual comparison, just executes key extractors, etc.
 */
abstract class ComparatorModel {
  private static final CallMatcher KEY_EXTRACTOR =
    anyOf(staticCall(JAVA_UTIL_COMPARATOR, "comparing", "comparingInt", "comparingLong", "comparingDouble").parameterCount(1),
          staticCall(JAVA_UTIL_COMPARATOR, "comparing").parameterCount(2));
  private static final CallMatcher NULL_HOSTILE = anyOf(staticCall(JAVA_UTIL_COMPARATOR, "naturalOrder", "reverseOrder").parameterCount(0),
                                                        staticCall(JAVA_UTIL_COLLECTIONS, "reverseOrder").parameterCount(0));
  private static final CallMatcher NULL_FRIENDLY = staticCall(JAVA_UTIL_COMPARATOR, "nullsFirst", "nullsLast").parameterCount(1);
  private static final CallMatcher REVERSED = instanceCall(JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);
  private static final CallMatcher REVERSE_ORDER = staticCall(JAVA_UTIL_COLLECTIONS, "reverseOrder").parameterCount(1);

  private final boolean myFailsOnNull;

  protected ComparatorModel(boolean failsOnNull) {
    myFailsOnNull = failsOnNull;
  }

  abstract void evaluate(CFGBuilder builder);

  abstract void invoke(CFGBuilder builder);

  boolean failsOnNull() {
    return myFailsOnNull;
  }

  private static class NullHostile extends ComparatorModel {
    NullHostile() {
      super(true);
    }

    @Override
    void evaluate(CFGBuilder builder) {}

    @Override
    void invoke(CFGBuilder builder) {
      builder.pop();
    }
  }

  private static class Unknown extends ComparatorModel {
    private final PsiExpression myExpression;

    Unknown(PsiExpression expression) {
      super(false);
      myExpression = expression;
    }

    @Override
    void evaluate(CFGBuilder builder) {
      builder.evaluateFunction(myExpression);
    }

    @Override
    void invoke(CFGBuilder builder) {
      builder.pushUnknown().invokeFunction(2, myExpression).pop();
    }
  }

  private static class NullFriendly extends ComparatorModel {
    private final ComparatorModel myDownstream;

    NullFriendly(ComparatorModel downstream) {
      super(false);
      myDownstream = downstream;
    }

    @Override
    void evaluate(CFGBuilder builder) {
      myDownstream.evaluate(builder);
    }

    @Override
    void invoke(CFGBuilder builder) {
      builder.dup().ifNotNull().chain(myDownstream::invoke).elseBranch().pop().end();
    }
  }

  private static final class KeyExtractor extends ComparatorModel {
    private final PsiExpression myKeyExtractor;
    private final ComparatorModel myDownstream;

    private KeyExtractor(PsiExpression keyExtractor, ComparatorModel downstream) {
      super(false);
      myKeyExtractor = keyExtractor;
      myDownstream = downstream;
    }

    @Override
    void evaluate(CFGBuilder builder) {
      builder.evaluateFunction(myKeyExtractor);
      myDownstream.evaluate(builder);
    }

    @Override
    void invoke(CFGBuilder builder) {
      builder.invokeFunction(1, myKeyExtractor, myDownstream.myFailsOnNull ? Nullability.NOT_NULL : Nullability.UNKNOWN)
        .chain(myDownstream::invoke);
    }
  }

  @NotNull
  static ComparatorModel from(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null || NULL_HOSTILE.matches(expression)) {
      return new NullHostile();
    }
    if (expression instanceof PsiReferenceExpression ref &&
        "CASE_INSENSITIVE_ORDER".equals(ref.getReferenceName()) &&
        ref.resolve() instanceof PsiField field && field.getContainingClass() != null &&
        CommonClassNames.JAVA_LANG_STRING.equals(field.getContainingClass().getQualifiedName())) {
      return new NullHostile();
    }
    PsiMethodCallExpression call = ObjectUtils.tryCast(expression, PsiMethodCallExpression.class);
    if (call == null) return new Unknown(expression);
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (REVERSED.test(call) && qualifier != null) {
      return from(qualifier);
    }
    if (REVERSE_ORDER.test(call)) {
      return from(call.getArgumentList().getExpressions()[0]);
    }
    if (NULL_FRIENDLY.test(call) && qualifier != null) {
      return new NullFriendly(from(qualifier));
    }
    if (KEY_EXTRACTOR.test(call)) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression keyExtractor = args[0];
      ComparatorModel downstream = args.length == 2 ? from(args[1]) : new NullHostile();
      return new KeyExtractor(keyExtractor, downstream);
    }
    return new Unknown(expression);
  }
}
