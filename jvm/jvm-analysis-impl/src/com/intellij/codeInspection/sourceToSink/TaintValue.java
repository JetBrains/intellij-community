// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

enum TaintValue implements RestrictionInfo {
  UNTAINTED(UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION, RestrictionInfoKind.KNOWN, null) {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
      return other;
    }
  },
  TAINTED(UntaintedAnnotationProvider.DEFAULT_TAINTED_ANNOTATION, RestrictionInfoKind.KNOWN,
          "jvm.inspections.source.unsafe.to.sink.flow.description") {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
      return this;
    }
  },
  UNKNOWN(UntaintedAnnotationProvider.DEFAULT_POLY_TAINTED_ANNOTATION, RestrictionInfoKind.UNKNOWN,
          "jvm.inspections.source.unknown.to.sink.flow.description") {
    @Override
    @NotNull
    public TaintValue join(@NotNull TaintValue other) {
      return other == TAINTED ? other : this;
    }
  };

  public static final Set<String> NAMES = ContainerUtil.map2Set(values(), v -> v.getAnnotationName());

  private final String myName;
  private final RestrictionInfoKind myKind;
  private final String myErrorMessage;

  TaintValue(@NotNull String name, @NotNull RestrictionInfoKind kind, @Nullable String errorMessage) {
    this.myName = name;
    this.myKind = kind;
    this.myErrorMessage = errorMessage;
  }

  public abstract @NotNull TaintValue join(@NotNull TaintValue other);

  @NotNull String getAnnotationName() {
    return myName;
  }

  @Override
  public @NotNull RestrictionInfoKind getKind() {
    return myKind;
  }

  String getErrorMessage() {
    return myErrorMessage;
  }
}
