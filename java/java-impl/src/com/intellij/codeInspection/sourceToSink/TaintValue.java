// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.codeInspection.sourceToSink.restriction.RestrictionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

interface TaintValue extends RestrictionInfo {

  TaintValue Untainted = new TaintValue() {

    @Override
    public @Nullable String getErrorMessage() {
      return null;
    }

    @Override
    public @NotNull TaintValue and(@NotNull TaintValue other) {
      return other;
    }

    @Override
    public @NotNull String getName() {
      return "org.checkerframework.checker.tainting.qual.Untainted";
    }

    @Override
    public String toString() {
      return "UNTAINTED";
    }
  };
  TaintValue Unknown = new TaintUnknown();

  TaintValue Tainted = new TaintValue() {

    @Override
    public @NotNull String getErrorMessage() {
      return "inspection.source.unsafe.to.sink.flow.description";
    }

    @Override
    public @NotNull TaintValue and(@NotNull TaintValue other) {
      return this;
    }

    @Override
    public @NotNull String getName() {
      return "org.checkerframework.checker.tainting.qual.Tainted";
    }

    @Override
    public String toString() {
      return "TAINTED";
    }
  };
  
  Set<String> NAMES = Set.of(Tainted.getName(), Untainted.getName(), Unknown.getName());

  @Nullable String getErrorMessage();

  @NotNull TaintValue and(@NotNull TaintValue other);

  @NotNull String getName();

  class TaintUnknown implements Unspecified, TaintValue {

    @Override
    public boolean isUnknown() {
      return true;
    }

    @Override
    public @NotNull String getErrorMessage() {
      return "inspection.source.unknown.to.sink.flow.description";
    }

    @Override
    public @NotNull TaintValue and(@NotNull TaintValue other) {
      return other == Tainted ? other : this;
    }

    @Override
    public @NotNull String getName() {
      return "org.checkerframework.checker.tainting.qual.PolyTainted";
    }

    @Override
    public String toString() {
      return "UNKNOWN";
    }
  }
}
