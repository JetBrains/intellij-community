// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractMethodSettingsImpl<T> implements ExtractMethodSettings<T> {
  private final @NotNull String myMethodName;
  private final AbstractVariableData @NotNull [] myVariableData;
  private final @Nullable T myVisibility;

  public ExtractMethodSettingsImpl(@NotNull String methodName,
                                   AbstractVariableData @NotNull [] abstractVariableData,
                                   @Nullable T visibility) {

    myMethodName = methodName;
    myVariableData = abstractVariableData;
    myVisibility = visibility;
  }

  @Override
  public @NotNull String getMethodName() {
    return myMethodName;
  }

  @Override
  public AbstractVariableData @NotNull [] getAbstractVariableData() {
    return myVariableData;
  }

  @Override
  public @Nullable T getVisibility() {
    return myVisibility;
  }
}
