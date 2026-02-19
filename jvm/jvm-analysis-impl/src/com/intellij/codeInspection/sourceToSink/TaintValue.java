// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import one.util.streamex.MoreCollectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.stream.Collector;

public enum TaintValue implements RestrictionInfo {
  UNTAINTED(RestrictionInfoKind.KNOWN) {
    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return other;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      return null;
    }
  },
  TAINTED(RestrictionInfoKind.KNOWN) {
    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return this;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      PsiModifierListOwner owner = context.getOwner();
      if (owner instanceof PsiMethod) return "jvm.inspections.source.to.sink.flow.returned.unsafe";
      if (owner instanceof PsiParameter) return "jvm.inspections.source.to.sink.flow.passed.unsafe";
      if (owner instanceof PsiLocalVariable) return "jvm.inspections.source.to.sink.flow.assigned.unsafe";
      return "jvm.inspections.source.to.sink.flow.common.unsafe";
    }
  },
  UNKNOWN(RestrictionInfoKind.UNKNOWN) {
    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return other == TAINTED ? other : this;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      PsiModifierListOwner owner = context.getOwner();
      if (owner instanceof PsiMethod) return "jvm.inspections.source.to.sink.flow.returned.unknown";
      if (owner instanceof PsiParameter) return "jvm.inspections.source.to.sink.flow.passed.unknown";
      if (owner instanceof PsiLocalVariable) return "jvm.inspections.source.to.sink.flow.assigned.unknown";
      return "jvm.inspections.source.to.sink.flow.common.unknown";
    }
  };

  private final RestrictionInfoKind myKind;

  TaintValue(@NotNull RestrictionInfoKind kind) {
    this.myKind = kind;
  }

  public abstract @NotNull TaintValue join(@NotNull TaintValue other);

  public @NotNull TaintValue joinUntil(@Nullable TaintValue until,
                                       @SuppressWarnings("BoundedWildcard") @NotNull Supplier<TaintValue> callable) {
    if (until == null) {
      until = TAINTED;
    }
    if (until == this) {
      return this;
    }
    if (until == UNKNOWN && this != UNTAINTED) {
      return this;
    }
    return join(callable.get());
  }

  public static @NotNull Collector<TaintValue, ?, TaintValue> joining() {
    return MoreCollectors.reducingWithZero(TAINTED, UNTAINTED, TaintValue::join);
  }

  @Override
  public @NotNull RestrictionInfoKind getKind() {
    return myKind;
  }

  abstract String getErrorMessage(@NotNull AnnotationContext context);
}
