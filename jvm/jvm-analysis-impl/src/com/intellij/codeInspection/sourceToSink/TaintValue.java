// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.MoreCollectors;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collector;

public enum TaintValue implements RestrictionInfo {
  UNTAINTED(UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION, RestrictionInfoKind.KNOWN) {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
      return other;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      return null;
    }
  },
  TAINTED(UntaintedAnnotationProvider.DEFAULT_TAINTED_ANNOTATION, RestrictionInfoKind.KNOWN) {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
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
  UNKNOWN(UntaintedAnnotationProvider.DEFAULT_POLY_TAINTED_ANNOTATION, RestrictionInfoKind.UNKNOWN) {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
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

  static final Set<String> NAMES = ContainerUtil.map2Set(values(), v -> v.getAnnotationName());

  private final String myName;
  private final RestrictionInfoKind myKind;

  TaintValue(@NotNull String name, @NotNull RestrictionInfoKind kind) {
    this.myName = name;
    this.myKind = kind;
  }

  public abstract @NotNull TaintValue join(@NotNull TaintValue other);

  @NotNull String getAnnotationName() {
    return myName;
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
