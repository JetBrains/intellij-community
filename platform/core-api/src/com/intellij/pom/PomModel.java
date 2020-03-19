// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.event.PomModelListener;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public interface PomModel extends UserDataHolder {
  <T extends PomModelAspect> T getModelAspect(@NotNull Class<T> aClass);

  void addModelListener(@NotNull PomModelListener listener);

  void addModelListener(@NotNull PomModelListener listener, @NotNull Disposable parentDisposable);

  void removeModelListener(@NotNull PomModelListener listener);

  void runTransaction(@NotNull PomTransaction transaction) throws IncorrectOperationException;
}