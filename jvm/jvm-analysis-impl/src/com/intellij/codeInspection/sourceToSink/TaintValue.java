// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

enum TaintValue implements RestrictionInfo {
  UNTAINTED(UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION, RestrictionInfoKind.KNOWN) {
    @Override
    @NotNull
    TaintValue join(@NotNull TaintValue other) {
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
    TaintValue join(@NotNull TaintValue other) {
      return this;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      return context.getOwner() instanceof PsiMethod ?
             "jvm.inspections.source.to.sink.flow.returned.unsafe" :
             "jvm.inspections.source.to.sink.flow.passed.unsafe";
    }
  },
  UNKNOWN(UntaintedAnnotationProvider.DEFAULT_POLY_TAINTED_ANNOTATION, RestrictionInfoKind.UNKNOWN) {
    @Override
    @NotNull
    TaintValue join(@NotNull TaintValue other) {
      return other == TAINTED ? other : this;
    }

    @Override
    String getErrorMessage(@NotNull AnnotationContext context) {
      return context.getOwner() instanceof PsiMethod ?
             "jvm.inspections.source.to.sink.flow.returned.unknown" :
             "jvm.inspections.source.to.sink.flow.passed.unknown";
    }
  };

  static final Set<String> NAMES = ContainerUtil.map2Set(values(), v -> v.getAnnotationName());

  private final String myName;
  private final RestrictionInfoKind myKind;

  TaintValue(@NotNull String name, @NotNull RestrictionInfoKind kind) {
    this.myName = name;
    this.myKind = kind;
  }

  abstract @NotNull TaintValue join(@NotNull TaintValue other);

  @NotNull String getAnnotationName() {
    return myName;
  }

  @Override
  public @NotNull RestrictionInfoKind getKind() {
    return myKind;
  }

  abstract String getErrorMessage(@NotNull AnnotationContext context);
}
