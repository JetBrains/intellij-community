// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.function.Consumer;

public final class ProjectLoadingErrorsHeadlessNotifier extends ProjectLoadingErrorsNotifier {
  private static Consumer<? super ConfigurationErrorDescription> ourErrorHandler;

  @TestOnly
  public static void setErrorHandler(@NotNull Disposable parentDisposable,
                                     @NotNull Consumer<? super ConfigurationErrorDescription> errorHandler) {
    ourErrorHandler = errorHandler;
    Disposer.register(parentDisposable, () -> ourErrorHandler = null);
  }

  @Override
  public void registerError(@NotNull ConfigurationErrorDescription errorDescription) {
    if (ourErrorHandler != null) {
      ourErrorHandler.accept(errorDescription);
    }
    else {
      throw new RuntimeException(errorDescription.getDescription());
    }
  }

  @Override
  public void registerErrors(@NotNull Collection<? extends ConfigurationErrorDescription> errorDescriptions) {
    for (ConfigurationErrorDescription description : errorDescriptions) {
      registerError(description);
    }
  }
}
