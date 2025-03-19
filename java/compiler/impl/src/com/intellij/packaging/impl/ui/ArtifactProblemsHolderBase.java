// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ArtifactProblemsHolder;
import org.jetbrains.annotations.NotNull;

public abstract class ArtifactProblemsHolderBase implements ArtifactProblemsHolder {
  private final PackagingElementResolvingContext myContext;

  protected ArtifactProblemsHolderBase(PackagingElementResolvingContext context) {
    myContext = context;
  }

  @Override
  public @NotNull PackagingElementResolvingContext getContext() {
    return myContext;
  }

  @Override
  public void registerError(@NotNull @NlsContexts.DialogMessage String message, @NotNull String problemTypeId) {
    registerError(message, problemTypeId, null);
  }
}
