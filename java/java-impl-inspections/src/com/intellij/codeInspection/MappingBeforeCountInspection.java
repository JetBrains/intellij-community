// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public final class MappingBeforeCountInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STREAM_COUNT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "count").parameterCount(0);
  private static final CallMatcher MAPPING_CALL =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "map", "mapToInt", "mapToLong", "mapToDouble", "mapToObj", "peek").parameterCount(1),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "boxed", "asLongStream", "asDoubleStream").parameterCount(0)
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
        if (!STREAM_COUNT.test(call)) return;
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (!MAPPING_CALL.test(qualifierCall)) return;
        PsiReferenceExpression ref = qualifierCall.getMethodExpression();
        PsiElement anchor = Objects.requireNonNull(ref.getReferenceNameElement());
        String name = Objects.requireNonNull(ref.getReferenceName());
        holder.registerProblem(anchor, JavaBundle.message("inspection.mapping.before.count.message", name),
                               new RedundantStreamOptionalCallInspection.RemoveCallFix(name));
      }
    };
  }
}
