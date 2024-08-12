// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.events.DerivedResult;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FailureResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class DerivedResultImpl implements DerivedResult {

  private final @NotNull Supplier<? extends EventResult> myOnDefault;
  private final @NotNull Supplier<? extends FailureResult> myFail;

  public DerivedResultImpl() {
    this(null, null);
  }

  public DerivedResultImpl(@Nullable Supplier<? extends EventResult> onDefault, @Nullable Supplier<? extends FailureResult> onFail) {
    myOnDefault = onDefault != null ? onDefault : SuccessResultImpl::new;
    myFail = onFail != null ? onFail : FailureResultImpl::new;
  }

  @Override
  public @NotNull FailureResult createFailureResult() {
    FailureResult result = myFail.get();
    if (result == null) {
      return new FailureResultImpl();
    }
    return result;
  }

  @Override
  public @NotNull EventResult createDefaultResult() {
    EventResult result = myOnDefault.get();
    if (result == null) {
      return new SuccessResultImpl();
    }
    return result;
  }
}
