// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.codeInspection.restriction.RestrictionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

interface TaintValue extends RestrictionInfo {

  TaintValue Untainted = new TaintValue() {

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.KNOWN;
    }

    @Override
    public @Nullable String getErrorMessage() {
      return null;
    }

    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return other;
    }

    @Override
    public @NotNull String getAnnotationName() {
      return UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION;
    }

    @Override
    public String toString() {
      return "UNTAINTED";
    }
  };
  TaintValue Unknown = new TaintUnknown();

  TaintValue Tainted = new TaintValue() {

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.KNOWN;
    }

    @Override
    public @NotNull String getErrorMessage() {
      return "jvm.inspections.source.unsafe.to.sink.flow.description";
    }

    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return this;
    }

    @Override
    public @NotNull String getAnnotationName() {
      return UntaintedAnnotationProvider.DEFAULT_TAINTED_ANNOTATION;
    }

    @Override
    public String toString() {
      return "TAINTED";
    }
  };
  
  Set<String> NAMES = Set.of(Tainted.getAnnotationName(), Untainted.getAnnotationName(), Unknown.getAnnotationName());

  @Nullable String getErrorMessage();

  @NotNull TaintValue join(@NotNull TaintValue other);

  @NotNull String getAnnotationName();

  class TaintUnknown implements TaintValue {

    @Override
    public @NotNull String getErrorMessage() {
      return "jvm.inspections.source.unknown.to.sink.flow.description";
    }

    @Override
    public @NotNull TaintValue join(@NotNull TaintValue other) {
      return other == Tainted ? other : this;
    }

    @Override
    public @NotNull String getAnnotationName() {
      return UntaintedAnnotationProvider.DEFAULT_POLY_TAINTED_ANNOTATION;
    }

    @Override
    public String toString() {
      return "UNKNOWN";
    }

    @Override
    public @NotNull RestrictionInfoKind getKind() {
      return RestrictionInfoKind.UNKNOWN;
    }
  }
}
