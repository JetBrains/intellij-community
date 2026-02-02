// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.siyeh.ig.psiutils.StreamApiUtil.findSubsequentCall;

public final class EndlessStreamInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> ALL_CONSUMING_OPERATIONS = Set.of(
    "sorted",
    "count",
    "reduce",
    "max",
    "min",
    "sum",
    "average",
    "collect",
    "toArray",
    "forEach",
    "summaryStatistics"
  );

  private static final Set<String> NON_LIMITING_OPERATIONS = Set.of(
    "filter",
    "map",
    "distinct",
    "flatMap",
    "flatMapToInt",
    "flatMapToLong",
    "flatMapToDouble",
    "flatMapToObj",
    "onClose",
    "peek",
    "skip",
    "dropWhile",
    "mapToDouble",
    "mapToLong",
    "mapToObj",
    "mapToInt",
    "parallel",
    "boxed",
    "sequential",
    "unordered",
    "asLongStream",
    "asDoubleStream"
  );

  private static final CallMatcher INFINITE_SOURCE = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "generate").parameterCount(1),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "iterate").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM, "generate").parameterCount(1),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM, "iterate").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM, "generate").parameterCount(1),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM, "iterate").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "generate").parameterCount(1),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "iterate").parameterCount(2),
    CallMatcher.instanceCall("java.util.Random", "ints", "longs", "doubles").parameterCount(2),
    CallMatcher.instanceCall("java.util.Random", "ints", "longs", "doubles").parameterCount(0)
  );

    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.STREAM_OPTIONAL);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!INFINITE_SOURCE.test(call)) return;
        PsiMethodCallExpression allConsumingCall = findSubsequentCall(call,
                                                                      name -> ALL_CONSUMING_OPERATIONS.contains(name),
                                                                      name -> NON_LIMITING_OPERATIONS.contains(name));
        if (allConsumingCall == null) return;
        PsiElement nameElement = allConsumingCall.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        holder.registerProblem(nameElement, JavaBundle.message("inspection.endless.stream.description"));
      }
    };
  }
}
