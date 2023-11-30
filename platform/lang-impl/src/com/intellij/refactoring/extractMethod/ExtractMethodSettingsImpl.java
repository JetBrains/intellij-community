// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.refactoring.util.AbstractVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractMethodSettingsImpl<T> implements ExtractMethodSettings<T> {
  @NotNull private final String myMethodName;
  private final AbstractVariableData @NotNull [] myVariableData;
  @Nullable private final T myVisibility;

  public ExtractMethodSettingsImpl(@NotNull String methodName,
                                   AbstractVariableData @NotNull [] abstractVariableData,
                                   @Nullable T visibility) {

    myMethodName = methodName;
    myVariableData = abstractVariableData;
    myVisibility = visibility;
  }

  @NotNull
  @Override
  public String getMethodName() {
    return myMethodName;
  }

  @Override
  public AbstractVariableData @NotNull [] getAbstractVariableData() {
    return myVariableData;
  }

  @Nullable
  @Override
  public T getVisibility() {
    return myVisibility;
  }
}
